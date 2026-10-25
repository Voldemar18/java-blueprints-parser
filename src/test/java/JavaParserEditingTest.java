import com.axenix.blueprints.model.Node;
import com.axenix.blueprints.service.JavaParserService;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.Test;

import static org.junit.Assert.*;

public class JavaParserEditingTest {

    private final JavaParserService service = new JavaParserService();

    private String findNodeId(String code, String type) {
        return service.parse(code)
                .getNodes()
                .stream()
                .filter(node -> type.equals(node.getType()))
                .map(Node::getId)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Не найден узел: " + type));
    }

    @Test
    public void changesReturnWithoutDeletingSameLineMethod() {
        String code =
                "public class Demo { " +
                        "public int sum(int a, int b) { return a + b; } " +
                        "}";

        String nodeId = findNodeId(code, "return");

        String updated = service.updateNode(
                code, nodeId, "a + b + 0"
        );

        CompilationUnit unit = StaticJavaParser.parse(updated);

        MethodDeclaration method = unit.getClassByName("Demo")
                .orElseThrow()
                .getMethodsByName("sum")
                .get(0);

        assertEquals(2, method.getParameters().size());

        assertEquals(
                "a + b + 0",
                method.getBody()
                        .orElseThrow()
                        .getStatement(0)
                        .asReturnStmt()
                        .getExpression()
                        .orElseThrow()
                        .toString()
        );
    }

    @Test
    public void changesOnlySelectedReturn() {
        String code = """
                public class Demo {
                    public int first() { return 1; }
                    public int second() { return 2; }
                }
                """;

        String firstReturnId = findNodeId(code, "return");

        String updated = service.updateNode(
                code, firstReturnId, "10"
        );

        var type = StaticJavaParser.parse(updated)
                .getClassByName("Demo")
                .orElseThrow();

        String firstExpression = type.getMethodsByName("first")
                .get(0)
                .getBody().orElseThrow()
                .getStatement(0).asReturnStmt()
                .getExpression().orElseThrow()
                .toString();

        String secondExpression = type.getMethodsByName("second")
                .get(0)
                .getBody().orElseThrow()
                .getStatement(0).asReturnStmt()
                .getExpression().orElseThrow()
                .toString();

        assertEquals("10", firstExpression);
        assertEquals("2", secondExpression);
    }

    @Test
    public void changesMultilineIfConditionWithoutDeletingBranches() {
        String code = """
                public class Demo {
                    public int choose(int a, int b) {
                        if (
                            a > 0 &&
                            b > 0
                        ) {
                            return 1;
                        } else {
                            return 2;
                        }
                    }
                }
                """;

        String nodeId = findNodeId(code, "if");

        String updated = service.updateNode(
                code, nodeId, "a >= 0 && b >= 0"
        );

        var method = StaticJavaParser.parse(updated)
                .getClassByName("Demo").orElseThrow()
                .getMethodsByName("choose").get(0);

        var condition = method.getBody().orElseThrow()
                .getStatement(0).asIfStmt();

        assertEquals(
                "a >= 0 && b >= 0",
                condition.getCondition().toString()
        );

        assertTrue(condition.getElseStmt().isPresent());

        assertEquals(
                "1",
                condition.getThenStmt().asBlockStmt()
                        .getStatement(0).asReturnStmt()
                        .getExpression().orElseThrow()
                        .toString()
        );

        assertEquals(
                "2",
                condition.getElseStmt().orElseThrow().asBlockStmt()
                        .getStatement(0).asReturnStmt()
                        .getExpression().orElseThrow()
                        .toString()
        );
    }

    @Test
    public void editInfoReturnsExpressionInsteadOfWholeLine() {
        String code =
                "public class Demo { int value() { return 7; } }";

        String nodeId = findNodeId(code, "return");

        var info = service.getNodeEditInfo(code, nodeId);

        assertEquals(nodeId, info.get("nodeId"));
        assertEquals("7", info.get("text"));
    }

    @Test
    public void rejectsUnsupportedClassEditing() {
        String code = "public class Demo {}";
        String nodeId = findNodeId(code, "class");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateNode(code, nodeId, "Other")
        );
    }

    @Test
    public void rejectsInvalidExpression() {
        String code =
                "public class Demo { int value() { return 7; } }";

        String nodeId = findNodeId(code, "return");

        assertThrows(
                com.github.javaparser.ParseProblemException.class,
                () -> service.updateNode(code, nodeId, "a +")
        );
    }
}