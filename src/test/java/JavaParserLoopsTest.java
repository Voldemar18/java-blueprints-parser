import com.axenix.blueprints.model.GraphResponse;
import com.axenix.blueprints.model.Node;
import com.axenix.blueprints.service.JavaParserService;
import org.junit.Test;

import static org.junit.Assert.*;

public class JavaParserLoopsTest {

    private final JavaParserService service = new JavaParserService();

    private Node find(GraphResponse graph, String type, String text) {
        return graph.getNodes().stream()
                .filter(node -> type.equals(node.getType()))
                .filter(node -> node.getText().contains(text))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Не найден узел: " + type + " " + text));
    }

    private boolean edge(
            GraphResponse graph,
            String source,
            String target,
            String label
    ) {
        return graph.getEdges().stream().anyMatch(item ->
                source.equals(item.getSource())
                        && target.equals(item.getTarget())
                        && label.equals(item.getLabel())
        );
    }

    @Test
    public void whileHasBodyBackEdgeAndExit() {
        GraphResponse graph = service.parse("""
                public class Demo {
                    void run(boolean ready) {
                        while (ready) {
                            tick();
                        }
                        done();
                    }
                    void tick() {}
                    void done() {}
                }
                """);

        Node loop = find(graph, "loop_while", "ready");
        Node tick = find(graph, "method_call", "tick()");
        Node done = find(graph, "method_call", "done()");

        assertTrue(edge(graph, loop.getId(), tick.getId(), "true"));
        assertTrue(edge(graph, tick.getId(), loop.getId(), "back"));
        assertTrue(edge(graph, loop.getId(), done.getId(), "false"));

        assertFalse(edge(graph, tick.getId(), done.getId(), "next"));
    }

    @Test
    public void forPassesThroughUpdateBeforeNextCondition() {
        GraphResponse graph = service.parse("""
                public class Demo {
                    void run() {
                        for (int i = 0; i < 3; i++) {
                            tick();
                        }
                        done();
                    }
                    void tick() {}
                    void done() {}
                }
                """);

        Node init = find(graph, "loop_init", "i");
        Node loop = find(graph, "loop_for", "i < 3");
        Node update = find(graph, "loop_update", "i++");
        Node tick = find(graph, "method_call", "tick()");
        Node done = find(graph, "method_call", "done()");

        assertTrue(edge(graph, init.getId(), loop.getId(), "next"));
        assertTrue(edge(graph, loop.getId(), tick.getId(), "true"));
        assertTrue(edge(graph, tick.getId(), update.getId(), "next"));
        assertTrue(edge(graph, update.getId(), loop.getId(), "back"));
        assertTrue(edge(graph, loop.getId(), done.getId(), "false"));
    }

    @Test
    public void foreachHasIterationAndExit() {
        GraphResponse graph = service.parse("""
                public class Demo {
                    void run(int[] values) {
                        for (int value : values) {
                            tick();
                        }
                        done();
                    }
                    void tick() {}
                    void done() {}
                }
                """);

        Node loop = find(graph, "loop_for_each", "values");
        Node tick = find(graph, "method_call", "tick()");
        Node done = find(graph, "method_call", "done()");

        assertTrue(edge(graph, loop.getId(), tick.getId(), "true"));
        assertTrue(edge(graph, tick.getId(), loop.getId(), "back"));
        assertTrue(edge(graph, loop.getId(), done.getId(), "false"));
    }
}