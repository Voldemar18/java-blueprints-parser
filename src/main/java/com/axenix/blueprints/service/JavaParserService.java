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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JavaParserService {

    public String updateNode(String code, String nodeId, String replacement) {
        GraphResponse graph = parse(code);
        Node selected = graph.getNodes().stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Узел графа не найден"));
        if (replacement == null || replacement.isBlank()) {
            throw new IllegalArgumentException("Новый текст узла не должен быть пустым");
        }
        String[] lines = code.split("\\R", -1);
        int line = selected.getLine() - 1;
        if (line < 0 || line >= lines.length) throw new IllegalArgumentException("У узла нет строки исходника");
        String indent = lines[line].substring(0, lines[line].length() - lines[line].stripLeading().length());
        lines[line] = indent + replacement.strip();
        String updated = String.join("\n", lines);
        StaticJavaParser.parse(updated);
        return updated;
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
        private String lastNodeId = null;

        public GraphVisitor(List<Node> nodes, List<Edge> edges, AtomicInteger idCounter) {
            this.nodes = nodes;
            this.edges = edges;
            this.idCounter = idCounter;
        }

        private String generateId() {
            return "node_" + idCounter.getAndIncrement();
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String id = generateId();
            nodes.add(new Node(id, "class", n.getNameAsString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            if (lastNodeId != null) edges.add(new Edge(lastNodeId, id, "contains"));
            lastNodeId = id;
            super.visit(n, arg);
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            String id = generateId();
            nodes.add(new Node(id, "field", n.toString().trim(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            String id = generateId();
            nodes.add(new Node(id, "method", n.getDeclarationAsString(false, false, false), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            if (lastNodeId != null) edges.add(new Edge(lastNodeId, id, "contains"));
            lastNodeId = id;
            super.visit(n, arg);
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            String id = generateId();
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_for", n.getInitialization() + " " + n.getCompare().orElse(null), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            String id = generateId();
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_for_each", n.getVariable() + " : " + n.getIterable(), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            String id = generateId();
            String complexity = calculateComplexity(n);
            nodes.add(new Node(id, "loop_while", n.getCondition().toString(), n.getBegin().map(p -> p.line).orElse(0), complexity));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        // ИСПРАВЛЕНО: Изолируем ветки if и else друг от друга
        @Override
        public void visit(IfStmt n, Void arg) {
            String ifId = generateId();
            nodes.add(new Node(ifId, "if", n.getCondition().toString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(ifId, "next");

            // Обрабатываем THEN-ветку (то, что внутри if)
            lastNodeId = ifId;
            n.getThenStmt().accept(this, arg);

            // Обрабатываем ELSE-ветку (если есть)
            if (n.getElseStmt().isPresent()) {
                String elseId = generateId();
                nodes.add(new Node(elseId, "else", "else block", n.getElseStmt().get().getBegin().map(p -> p.line).orElse(0), "O(1)"));

                // Стрелка от IF к ELSE с меткой "false"
                edges.add(new Edge(ifId, elseId, "false"));

                // Теперь узлы внутри else будут связаны с else
                lastNodeId = elseId;
                n.getElseStmt().get().accept(this, arg);
            }

            // ВАЖНО: Мы НЕ вызываем super.visit(n, arg),
            // так как мы уже вручную обошли then и else.
            // Если вызвать super, JavaParser обойдет их повторно, создав дубликаты!
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            String id = generateId();
            nodes.add(new Node(id, "switch", n.getSelector().toString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(ReturnStmt n, Void arg) {
            String id = generateId();
            nodes.add(new Node(id, "return", n.getExpression().map(Object::toString).orElse("void"), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String id = generateId();
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
            String id = generateId();
            nodes.add(new Node(id, "variable", n.toString(), n.getBegin().map(p -> p.line).orElse(0), "O(1)"));
            linkToPrevious(id, "next");
            super.visit(n, arg);
        }

        private void linkToPrevious(String currentId, String label) {
            if (lastNodeId != null) edges.add(new Edge(lastNodeId, currentId, label));
            lastNodeId = currentId;
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
