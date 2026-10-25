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

        private final Map<String, com.github.javaparser.ast.Node> astNodes =
                new LinkedHashMap<>();

        private record FlowExit(String nodeId, String label) {
        }

        private static class ControlContext {
            final String continueTarget;
            final List<FlowExit> breaks = new ArrayList<>();

            ControlContext(String continueTarget) {
                this.continueTarget = continueTarget;
            }
        }

        private final java.util.Deque<ControlContext> controls =
                new java.util.ArrayDeque<>();

        private List<FlowExit> exits = new ArrayList<>();
        private String currentClassId;

        public GraphVisitor(
                List<Node> nodes,
                List<Edge> edges,
                AtomicInteger idCounter
        ) {
            this.nodes = nodes;
            this.edges = edges;
            this.idCounter = idCounter;
        }

        private String generateId() {
            return "node_" + idCounter.getAndIncrement();
        }

        private String register(com.github.javaparser.ast.Node astNode) {
            String id = generateId();
            astNodes.put(id, astNode);
            return id;
        }

        private int line(com.github.javaparser.ast.Node node) {
            return node.getBegin().map(position -> position.line).orElse(0);
        }

        private String addNode(
                com.github.javaparser.ast.Node astNode,
                String type,
                String text
        ) {
            String id = register(astNode);
            nodes.add(new Node(id, type, text, line(astNode), "unknown"));
            return id;
        }

        private String addSyntheticNode(String type, String text, int line) {
            String id = generateId();
            nodes.add(new Node(id, type, text, line, "unknown"));
            return id;
        }

        private void setExit(String id, String label) {
            exits = new ArrayList<>();
            exits.add(new FlowExit(id, label));
        }

        private void connectExitsTo(String targetId, String defaultLabel) {
            for (FlowExit exit : exits) {
                edges.add(new Edge(
                        exit.nodeId(),
                        targetId,
                        exit.label() == null ? defaultLabel : exit.label()
                ));
            }
        }

        private void linkToPrevious(String id, String label) {
            connectExitsTo(id, label);
            setExit(id, null);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String id = addNode(
                    n,
                    n.isInterface() ? "interface" : "class",
                    n.getNameAsString()
            );

            String previousClass = currentClassId;
            List<FlowExit> previousExits = exits;

            if (previousClass != null) {
                edges.add(new Edge(previousClass, id, "contains"));
            }

            currentClassId = id;
            exits = new ArrayList<>();

            for (var member : n.getMembers()) {
                member.accept(this, arg);
            }

            currentClassId = previousClass;
            exits = previousExits;
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            String id = addNode(n, "field", n.toString().trim());

            if (currentClassId != null) {
                edges.add(new Edge(currentClassId, id, "contains"));
            }
        }

        private void visitExecutable(
                com.github.javaparser.ast.Node declaration,
                String type,
                String text,
                BlockStmt body,
                Void arg
        ) {
            String id = addNode(declaration, type, text);

            if (currentClassId != null) {
                edges.add(new Edge(currentClassId, id, "contains"));
            }

            List<FlowExit> savedExits = exits;
            List<ControlContext> savedControls = new ArrayList<>(controls);

            controls.clear();
            setExit(id, "entry");

            try {
                if (body != null) {
                    body.accept(this, arg);
                }
            } finally {
                exits = savedExits;
                controls.clear();
                controls.addAll(savedControls);
            }
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            visitExecutable(
                    n,
                    "method",
                    n.getDeclarationAsString(false, false, false),
                    n.getBody().orElse(null),
                    arg
            );
        }

        @Override
        public void visit(
                com.github.javaparser.ast.body.ConstructorDeclaration n,
                Void arg
        ) {
            visitExecutable(
                    n,
                    "constructor",
                    n.getNameAsString() + n.getParameters(),
                    n.getBody(),
                    arg
            );
        }

        @Override
        public void visit(BlockStmt n, Void arg) {
            for (Statement statement : n.getStatements()) {
                // Не создаём обычное продолжение после return/break/continue.
                if (exits.isEmpty()) {
                    break;
                }
                statement.accept(this, arg);
            }
        }

        @Override
        public void visit(ExpressionStmt n, Void arg) {
            Expression expression = n.getExpression();

            if (expression instanceof MethodCallExpr
                    || expression instanceof VariableDeclarationExpr) {
                expression.accept(this, arg);
            } else {
                String id = addNode(n, "operation", expression.toString());
                linkToPrevious(id, "next");
            }
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            // Весь вызов представлен одним узлом.
            String id = addNode(n, "method_call", n.toString());
            linkToPrevious(id, "next");
        }

        @Override
        public void visit(VariableDeclarationExpr n, Void arg) {
            String id = addNode(n, "variable", n.toString());
            linkToPrevious(id, "next");
        }

        @Override
        public void visit(IfStmt n, Void arg) {
            String id = addNode(n, "if", n.getCondition().toString());
            linkToPrevious(id, "next");

            setExit(id, "true");
            n.getThenStmt().accept(this, arg);
            List<FlowExit> thenExits = new ArrayList<>(exits);

            setExit(id, "false");

            if (n.getElseStmt().isPresent()) {
                Statement statement = n.getElseStmt().get();
                String elseId = addNode(statement, "else", "else");

                linkToPrevious(elseId, "next");
                statement.accept(this, arg);
            }

            List<FlowExit> elseExits = new ArrayList<>(exits);

            exits = new ArrayList<>();
            exits.addAll(thenExits);
            exits.addAll(elseExits);
        }

        private void visitLoop(
                Statement astNode,
                Statement body,
                String type,
                String condition,
                String initialization,
                String update,
                boolean hasFalseExit,
                Void arg
        ) {
            int sourceLine = line(astNode);

            if (initialization != null && !initialization.isBlank()) {
                String initId = addSyntheticNode(
                        "loop_init", initialization, sourceLine
                );
                linkToPrevious(initId, "next");
            }

            String conditionId = addNode(astNode, type, condition);
            linkToPrevious(conditionId, "next");

            boolean hasUpdate = update != null && !update.isBlank();
            String updateId = hasUpdate ? generateId() : null;

            ControlContext context = new ControlContext(
                    hasUpdate ? updateId : conditionId
            );

            controls.push(context);

            try {
                setExit(conditionId, "true");
                body.accept(this, arg);

                if (hasUpdate) {
                    nodes.add(new Node(
                            updateId,
                            "loop_update",
                            update,
                            sourceLine,
                            "unknown"
                    ));

                    connectExitsTo(updateId, "next");
                    setExit(updateId, null);
                }

                connectExitsTo(conditionId, "back");

            } finally {
                controls.pop();
            }

            exits = new ArrayList<>();

            if (hasFalseExit) {
                exits.add(new FlowExit(conditionId, "false"));
            }

            exits.addAll(context.breaks);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            visitLoop(
                    n, n.getBody(),
                    "loop_while",
                    n.getCondition().toString(),
                    null, null, true, arg
            );
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            visitLoop(
                    n, n.getBody(),
                    "loop_for",
                    n.getCompare().map(Object::toString).orElse("true"),
                    n.getInitialization().isEmpty()
                            ? null : n.getInitialization().toString(),
                    n.getUpdate().isEmpty()
                            ? null : n.getUpdate().toString(),
                    n.getCompare().isPresent(),
                    arg
            );
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            String sourceId = addSyntheticNode(
                    "loop_source",
                    "Источник: " + n.getIterable(),
                    line(n)
            );
            linkToPrevious(sourceId, "next");

            visitLoop(
                    n, n.getBody(),
                    "loop_for_each",
                    n.getVariable() + " : " + n.getIterable(),
                    null, null, true, arg
            );
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            String bodyId = addSyntheticNode(
                    "loop_body", "do", line(n)
            );
            linkToPrevious(bodyId, "next");

            String conditionId = register(n);
            ControlContext context = new ControlContext(conditionId);

            controls.push(context);

            try {
                setExit(bodyId, null);
                n.getBody().accept(this, arg);

                nodes.add(new Node(
                        conditionId,
                        "loop_do_while",
                        n.getCondition().toString(),
                        line(n),
                        "unknown"
                ));

                connectExitsTo(conditionId, "next");
                edges.add(new Edge(conditionId, bodyId, "true"));

            } finally {
                controls.pop();
            }

            setExit(conditionId, "false");
            exits.addAll(context.breaks);
        }

        @Override
        public void visit(BreakStmt n, Void arg) {
            if (n.getLabel().isPresent()) {
                throw new IllegalArgumentException(
                        "break с меткой пока не поддерживается графом"
                );
            }

            if (controls.isEmpty()) {
                throw new IllegalArgumentException(
                        "break вне поддерживаемого цикла или switch"
                );
            }

            String id = addNode(n, "break", "break");
            linkToPrevious(id, "next");

            controls.peek().breaks.add(new FlowExit(id, "break"));
            exits = new ArrayList<>();
        }

        @Override
        public void visit(ContinueStmt n, Void arg) {
            if (n.getLabel().isPresent()) {
                throw new IllegalArgumentException(
                        "continue с меткой пока не поддерживается графом"
                );
            }

            ControlContext loop = null;

            for (ControlContext context : controls) {
                if (context.continueTarget != null) {
                    loop = context;
                    break;
                }
            }

            if (loop == null) {
                throw new IllegalArgumentException(
                        "continue вне поддерживаемого цикла"
                );
            }

            String id = addNode(n, "continue", "continue");
            linkToPrevious(id, "next");

            edges.add(new Edge(id, loop.continueTarget, "continue"));
            exits = new ArrayList<>();
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            String switchId = addNode(
                    n, "switch", n.getSelector().toString()
            );
            linkToPrevious(switchId, "next");

            ControlContext context = new ControlContext(null);
            controls.push(context);

            List<FlowExit> fallThrough = new ArrayList<>();
            List<FlowExit> completedRules = new ArrayList<>();
            boolean hasDefault = false;

            try {
                for (SwitchEntry entry : n.getEntries()) {
                    boolean isDefault = entry.getLabels().isEmpty();
                    hasDefault |= isDefault;

                    String label = isDefault
                            ? "default"
                            : "case " + entry.getLabels();

                    String caseId = addNode(entry, "case", label);

                    edges.add(new Edge(switchId, caseId, label));

                    exits = new ArrayList<>(fallThrough);
                    connectExitsTo(caseId, "fallthrough");
                    setExit(caseId, null);

                    for (Statement statement : entry.getStatements()) {
                        if (exits.isEmpty()) {
                            break;
                        }
                        statement.accept(this, arg);
                    }

                    if (entry.getType() == SwitchEntry.Type.STATEMENT_GROUP) {
                        fallThrough = new ArrayList<>(exits);
                    } else {
                        completedRules.addAll(exits);
                        fallThrough = new ArrayList<>();
                    }
                }
            } finally {
                controls.pop();
            }

            exits = new ArrayList<>();
            exits.addAll(fallThrough);
            exits.addAll(completedRules);
            exits.addAll(context.breaks);

            if (!hasDefault) {
                exits.add(new FlowExit(switchId, "no_match"));
            }
        }

        @Override
        public void visit(ReturnStmt n, Void arg) {
            String id = addNode(
                    n,
                    "return",
                    n.getExpression().map(Object::toString).orElse("void")
            );

            linkToPrevious(id, "next");
            exits = new ArrayList<>();
        }

        @Override
        public void visit(ThrowStmt n, Void arg) {
            String id = addNode(n, "throw", n.getExpression().toString());
            linkToPrevious(id, "next");

            exits = new ArrayList<>();
        }
    }
}
