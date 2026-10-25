import com.axenix.blueprints.model.GraphResponse;
import com.axenix.blueprints.model.Node;
import com.axenix.blueprints.service.JavaParserService;
import org.junit.Test;

import static org.junit.Assert.*;

public class JavaParserJumpsTest {

    private final JavaParserService service = new JavaParserService();

    private Node find(GraphResponse graph, String type, String text) {
        return graph.getNodes().stream()
                .filter(node -> type.equals(node.getType()))
                .filter(node -> node.getText().contains(text))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Не найден: " + type + " " + text));
    }

    private boolean edge(
            GraphResponse graph,
            String from,
            String to,
            String label
    ) {
        return graph.getEdges().stream().anyMatch(item ->
                from.equals(item.getSource())
                        && to.equals(item.getTarget())
                        && label.equals(item.getLabel())
        );
    }

    @Test
    public void continueInForGoesToUpdateAndBreakGoesOutsideLoop() {
        GraphResponse graph = service.parse("""
                public class Demo {
                    void run() {
                        for (int i = 0; i < 10; i++) {
                            if (i == 2) continue;
                            if (i == 5) break;
                            tick();
                        }
                        done();
                    }
                    void tick() {}
                    void done() {}
                }
                """);

        Node continuation = find(graph, "continue", "continue");
        Node update = find(graph, "loop_update", "i++");
        Node stopping = find(graph, "break", "break");
        Node done = find(graph, "method_call", "done()");

        assertTrue(edge(
                graph, continuation.getId(), update.getId(), "continue"
        ));

        assertTrue(edge(
                graph, stopping.getId(), done.getId(), "break"
        ));
    }

    @Test
    public void breakInsideSwitchDoesNotExitEnclosingWhile() {
        GraphResponse graph = service.parse("""
                public class Demo {
                    void run(int value) {
                        while (value > 0) {
                            switch (value) {
                                case 1:
                                    break;
                                default:
                                    tick();
                            }
                            afterSwitch();
                            value--;
                        }
                        done();
                    }
                    void tick() {}
                    void afterSwitch() {}
                    void done() {}
                }
                """);

        Node stopping = find(graph, "break", "break");
        Node afterSwitch = find(graph, "method_call", "afterSwitch()");
        Node done = find(graph, "method_call", "done()");

        assertTrue(edge(
                graph, stopping.getId(), afterSwitch.getId(), "break"
        ));

        assertFalse(edge(
                graph, stopping.getId(), done.getId(), "break"
        ));
    }
}