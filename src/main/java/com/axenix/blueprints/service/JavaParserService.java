package com.axenix.blueprints.service;

import com.axenix.blueprints.model.Edge;
import com.axenix.blueprints.model.GraphResponse;
import com.axenix.blueprints.model.Node;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Service;
import com.github.javaparser.ast.expr.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JavaParserService {

    private record EditContext(
            CompilationUnit unit,
            com.github.javaparser.ast.Node target
    ) {
    }

    private EditContext findEditTarget(String code, String nodeId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Исходный код пуст");
        }

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Не указан ID узла");
        }

        CompilationUnit unit = StaticJavaParser.parse(code);

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        GraphVisitor visitor = new GraphVisitor(
                nodes,
                edges,
                new AtomicInteger(1)
        );

        unit.accept(visitor, null);

        com.github.javaparser.ast.Node target =
                visitor.astNodes.get(nodeId);

        if (target == null) {
            throw new IllegalArgumentException(
                    "Узел не найден. Перестройте граф для текущего исходника"
            );
        }

        return new EditContext(unit, target);
    }

    private Expression editableExpression(
            com.github.javaparser.ast.Node target
    ) {
        if (target instanceof ReturnStmt statement) {
            return statement.getExpression().orElseThrow(
                    () -> new IllegalArgumentException(
                            "return без выражения пока не редактируется"
                    )
            );
        }

        if (target instanceof IfStmt statement) {
            return statement.getCondition();
        }

        if (target instanceof WhileStmt statement) {
            return statement.getCondition();
        }

        if (target instanceof ForStmt statement) {
            return statement.getCompare().orElseThrow(
                    () -> new IllegalArgumentException(
                            "for без условия пока не редактируется"
                    )
            );
        }

        if (target instanceof ForEachStmt statement) {
            return statement.getIterable();
        }

        if (target instanceof SwitchStmt statement) {
            return statement.getSelector();
        }

        throw new IllegalArgumentException(
                "Редактирование этого типа узла пока не поддерживается: "
                        + target.getClass().getSimpleName()
        );
    }

    private String editLabel(
            com.github.javaparser.ast.Node target
    ) {
        if (target instanceof ReturnStmt) {
            return "Выражение return — без return и точки с запятой";
        }
        if (target instanceof IfStmt) {
            return "Условие if — только выражение";
        }
        if (target instanceof WhileStmt) {
            return "Условие while — только выражение";
        }
        if (target instanceof ForStmt) {
            return "Условие for — только выражение сравнения";
        }
        if (target instanceof ForEachStmt) {
            return "Перебираемое выражение for-each";
        }
        if (target instanceof SwitchStmt) {
            return "Выражение switch";
        }

        return "Выражение";
    }

    public Map<String, String> getNodeEditInfo(
            String code,
            String nodeId
    ) {
        EditContext context = findEditTarget(code, nodeId);
        Expression expression = editableExpression(context.target());

        return Map.of(
                "nodeId", nodeId,
                "label", editLabel(context.target()),
                "text", expression.toString()
        );
    }

    public String updateNode(
            String code,
            String nodeId,
            String replacement
    ) {
        if (replacement == null || replacement.isBlank()) {
            throw new IllegalArgumentException(
                    "Новое выражение не должно быть пустым"
            );
        }

        EditContext context = findEditTarget(code, nodeId);

        Expression previous = editableExpression(context.target());
        Expression updatedExpression =
                StaticJavaParser.parseExpression(replacement.strip());

        if (!previous.replace(updatedExpression)) {
            throw new IllegalArgumentException(
                    "Не удалось заменить выражение выбранного узла"
            );
        }

        String updatedCode = context.unit().toString();

        StaticJavaParser.parse(updatedCode);

        return updatedCode;
    }

    public GraphResponse parse(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        AtomicInteger idCounter = new AtomicInteger(1);

        cu.accept(new GraphVisitor(nodes, edges, idCounter), null);

        return new GraphResponse(nodes, edges);
    }

    private static class GraphVisitor extends VoidVisitorAdapter<Void> {
        private final List<Node> nodes;
        private final List<Edge> edges;
        private final AtomicInteger idCounter;
        private record FlowExit(String nodeId, String label) {
        }

        private List<FlowExit> exits = new ArrayList<>();

        private String currentClassId = null;
        private final Map<String, com.github.javaparser.ast.Node> astNodes = new LinkedHashMap<>();

        public GraphVisitor(List<Node> nodes, List<Edge> edges, AtomicInteger idCounter) {
            this.nodes = nodes;
            this.edges = edges;
            this.idCounter = idCounter;
        }

        private String generateId() {
            return "node_" + idCounter.getAndIncrement();
        }

        private String register(
                com.github.javaparser.ast.Node astNode
        ) {
            String id = generateId();
            astNodes.put(id, astNode);
            return id;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String id = register(n);

            nodes.add(new Node(
                    id,
                    n.isInterface() ? "interface" : "class",
                    n.getNameAsString(),
                    n.getBegin().map(p -> p.line).orElse(0),
                    "unknown"
            ));

            String previousClassId = currentClassId;
            List<FlowExit> previousExits = exits;

            if (previousClassId != null) {
                edges.add(new Edge(previousClassId, id, "contains"));
            }

            currentClassId = id;
            exits = new ArrayList<>();

            // Обходим объявления членов класса.
            for (var member : n.getMembers()) {
                member.accept(this, arg);
            }

            currentClassId = previousClassId;
            exits = previousExits;
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            String id = register(n);
            nodes.add(new Node(id, "field", n.toString().trim(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            String id = register(n);

            nodes.add(new Node(
                    id,
                    "method",
                    n.getDeclarationAsString(false, false, false),
                    n.getBegin().map(p -> p.line).orElse(0),
                    "unknown"
            ));

            if (currentClassId != null) {
                edges.add(new Edge(currentClassId, id, "contains"));
            }

            List<FlowExit> previousExits = exits;

            exits = new ArrayList<>();
            exits.add(new FlowExit(id, "entry"));

            n.getBody().ifPresent(body -> body.accept(this, arg));

            // Выходы метода не становятся входом следующего метода.
            exits = previousExits;
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            String id = register(n);
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_for", n.getInitialization() + " " + n.getCompare().orElse(null), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            String id = register(n);
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_for_each", n.getVariable() + " : " + n.getIterable(), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            String id = register(n);
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_while", n.getCondition().toString(), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            String ifId = register(n);

            nodes.add(new Node(
                    ifId,
                    "if",
                    n.getCondition().toString(),
                    n.getBegin().map(p -> p.line).orElse(0),
                    "unknown"
            ));

            linkToPrevious(ifId, "next");

            exits = new ArrayList<>();
            exits.add(new FlowExit(ifId, "true"));

            n.getThenStmt().accept(this, arg);

            List<FlowExit> thenExits = new ArrayList<>(exits);

            // Вход в ELSE или обход THEN, если ELSE отсутствует.
            exits = new ArrayList<>();
            exits.add(new FlowExit(ifId, "false"));

            if (n.getElseStmt().isPresent()) {
                Statement elseStatement = n.getElseStmt().get();

                String elseId = register(elseStatement);

                nodes.add(new Node(
                        elseId,
                        "else",
                        "else",
                        elseStatement.getBegin().map(p -> p.line).orElse(0),
                        "unknown"
                ));

                linkToPrevious(elseId, "next");

                elseStatement.accept(this, arg);
            }

            List<FlowExit> elseExits = new ArrayList<>(exits);

            exits = new ArrayList<>();
            exits.addAll(thenExits);
            exits.addAll(elseExits);
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            String id = register(n);
            nodes.add(new Node(id, "switch", n.getSelector().toString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(ReturnStmt n, Void arg) {
            n.getExpression().ifPresent(expression ->
                    expression.accept(this, arg)
            );

            String id = register(n);

            nodes.add(new Node(
                    id,
                    "return",
                    n.getExpression().map(Object::toString).orElse("void"),
                    n.getBegin().map(p -> p.line).orElse(0),
                    "unknown"
            ));

            linkToPrevious(id, "next");

            exits = new ArrayList<>();
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String id = register(n);
            String callText = (n.getScope().isPresent() ? n.getScope().get() + "." : "") + n.getNameAsString() + "()";
            nodes.add(new Node(id, "method_call", callText, n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(VariableDeclarationExpr n, Void arg) {
            if (n.getParentNode().isPresent()) {
                com.github.javaparser.ast.Node parent = n.getParentNode().get();
                if (parent instanceof ForStmt || parent instanceof ForEachStmt) {
                    return;
                }
            }
            String id = register(n);
            nodes.add(new Node(id, "variable", n.toString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        private void linkToPrevious(String currentId, String defaultLabel) {
            for (FlowExit exit : exits) {
                String label = exit.label() == null
                        ? defaultLabel
                        : exit.label();

                edges.add(new Edge(
                        exit.nodeId(),
                        currentId,
                        label
                ));
            }

            exits = new ArrayList<>();
            exits.add(new FlowExit(currentId, null));
        }

        // --- ЛОГИКА BIG O ---
        private String calculateComplexity(com.github.javaparser.ast.Node node) {
            long innerLoopsCount = node.findAll(ForStmt.class).size() +
                    node.findAll(ForEachStmt.class).size() +
                    node.findAll(WhileStmt.class).size();

            if (node instanceof ForStmt || node instanceof ForEachStmt || node instanceof WhileStmt) {
                innerLoopsCount--;
            }

            if (innerLoopsCount > 0) {
                return "O(n^2)";
            }

            if (node instanceof ForStmt || node instanceof ForEachStmt || node instanceof WhileStmt) {
                return "O(n)";
            }

            return "O(1)";
        }
    }
}
