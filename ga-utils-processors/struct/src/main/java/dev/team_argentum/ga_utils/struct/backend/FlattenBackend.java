package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlattenBackend extends AbstractJavaParserBackend {

    @Override
    public String name() {
        return "flatten";
    }

    @Override
    public String generatedClassName(StructIr s) {
        return s.name + "_Dod";
    }

    @Override
    protected List<String[]> expandParam(StructIr s, String varName) {
        List<String[]> result = new ArrayList<>();
        for (FieldIr f : s.fields) {
            result.add(new String[]{f.type, varName + "_" + f.name});
        }
        return result;
    }

    @Override
    protected List<Expression> expandVar(StructIr s, String varName) {
        List<Expression> result = new ArrayList<>();
        for (FieldIr f : s.fields) {
            result.add(new NameExpr(varName + "_" + f.name));
        }
        return result;
    }

    @Override
    protected Expression fieldExpr(StructIr s, String varName, FieldIr f) {
        return new NameExpr(varName + "_" + f.name);
    }

    @Override
    protected Expression bareStructVarRef(NameExpr n, StructIr s, String varName, ProblemCollector problems, String path) {
        problems.error(path, StructFrontend.line(n),
                "direct use of @Struct value '" + varName + "' is not supported by the flatten backend");
        return n;
    }

    @Override
    public Expression finishStructCall(MethodCallExpr call, StructIr s, MethodIr m, List<Expression> args) {
        call.setScope(new NameExpr(generatedClassName(s)));
        call.setName(s.name + "_" + m.name());
        call.setArguments(new NodeList<>(args));
        return call;
    }

    @Override
    protected boolean supportsStructLocals(StructIr s) {
        return false;
    }

    @Override
    protected boolean supportsFieldLocalExpansion(StructIr s) {
        return true;
    }

    @Override
    protected Node expandFieldLocalDeclaration(CallerRewriter rewriter,
                                               com.github.javaparser.ast.stmt.ExpressionStmt stmt,
                                               com.github.javaparser.ast.expr.VariableDeclarationExpr decl, StructIr s) {
        List<com.github.javaparser.ast.stmt.Statement> expanded = new ArrayList<>();
        for (VariableDeclarator d : decl.getVariables()) {
            String varName = d.getNameAsString();
            Expression init = d.getInitializer().orElse(null);
            for (FieldIr f : s.fields) {
                VariableDeclarator nd = new VariableDeclarator(StaticJavaParser.parseType(f.type), varName + "_" + f.name);
                if (init instanceof ObjectCreationExpr oce && rewriter.structByType(oce.getType().asString()) == s) {
                    nd.setInitializer(defaultValue(f.type));
                } else if (init instanceof NameExpr ne && rewriter.vars.get(ne.getNameAsString()) == s) {
                    nd.setInitializer(new NameExpr(ne.getNameAsString() + "_" + f.name));
                } else if (init != null) {
                    rewriter.problems.error(rewriter.path, StructFrontend.line(stmt),
                            "unsupported initializer for @Struct local '" + varName + "' of struct " + s.name);
                }
                expanded.add(new com.github.javaparser.ast.stmt.ExpressionStmt(
                        new com.github.javaparser.ast.expr.VariableDeclarationExpr(new NodeList<>(nd))));
            }
            rewriter.vars.put(varName, s);
            rewriter.structLocals.add(varName);
        }
        stmt.setExpression(((com.github.javaparser.ast.stmt.ExpressionStmt) expanded.get(0)).getExpression());
        if (expanded.size() > 1 && stmt.getParentNode().orElse(null) instanceof com.github.javaparser.ast.stmt.BlockStmt parent) {
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.stmt.Statement> list = parent.getStatements();
            int idx = list.indexOf(stmt);
            for (int i = 1; i < expanded.size(); i++) {
                list.add(idx + i, expanded.get(i));
            }
        }
        return stmt;
    }

    protected static Expression defaultValue(String type) {
        if (type.contains("[]")) {
            return new com.github.javaparser.ast.expr.NullLiteralExpr();
        }
        String t = type.trim();
        if (t.equals("boolean")) {
            return new com.github.javaparser.ast.expr.BooleanLiteralExpr(false);
        }
        if (t.equals("byte") || t.equals("short") || t.equals("int") || t.equals("long")
                || t.equals("float") || t.equals("double") || t.equals("char")) {
            return new com.github.javaparser.ast.expr.IntegerLiteralExpr("0");
        }
        return new com.github.javaparser.ast.expr.NullLiteralExpr();
    }

    @Override
    protected void fieldWriteCheck(Node fieldAccessNode, StructIr s, FieldIr f, boolean isLocalVar,
                                   ProblemCollector problems, String path) {
        Node parent = fieldAccessNode.getParentNode().orElse(null);
        boolean write = false;
        if (parent instanceof AssignExpr ae && ae.getTarget() == fieldAccessNode) {
            write = true;
        } else if (parent instanceof UnaryExpr ue && isIncDec(ue.getOperator()) && ue.getExpression() == fieldAccessNode) {
            write = true;
        }
        if (write && f.scalar && !isLocalVar) {
            problems.error(path, StructFrontend.line(fieldAccessNode),
                    "flatten backend cannot persist a write to scalar field '" + f.name + "' of struct " + s.name
                            + " (the value would not survive the call boundary; use the soa or ssa backend)");
        }
    }

    protected static boolean isIncDec(UnaryExpr.Operator op) {
        return op == UnaryExpr.Operator.POSTFIX_INCREMENT
                || op == UnaryExpr.Operator.PREFIX_INCREMENT
                || op == UnaryExpr.Operator.POSTFIX_DECREMENT
                || op == UnaryExpr.Operator.PREFIX_DECREMENT;
    }

    protected static Set<String> mutatedScalarFields(StructIr s, MethodIr m) {
        Set<String> result = new HashSet<>();
        BlockStmt body;
        try {
            body = StaticJavaParser.parseBlock(m.normalizedBody());
        } catch (ParseProblemException e) {
            throw new IllegalStateException("failed to reparse normalized body of " + s.name + "." + m.name(), e);
        }
        for (AssignExpr a : body.findAll(AssignExpr.class)) {
            addIfScalarMutation(result, s, a.getTarget());
        }
        for (UnaryExpr u : body.findAll(UnaryExpr.class)) {
            if (isIncDec(u.getOperator())) {
                addIfScalarMutation(result, s, u.getExpression());
            }
        }
        return result;
    }

    private static void addIfScalarMutation(Set<String> out, StructIr s, Expression target) {
        String fieldName = placeholderField(target);
        if (fieldName != null) {
            FieldIr f = s.field(fieldName);
            if (f != null && f.scalar) {
                out.add(fieldName);
            }
        }
    }

    @Override
    public List<SourceFile> generateStructSources(List<StructIr> structs, Map<String, String> options, ProblemCollector problems) {
        List<SourceFile> out = new ArrayList<>();
        for (StructIr s : structs) {
            StringBuilder sb = new StringBuilder();
            if (!s.packageName.isEmpty()) {
                sb.append("package ").append(s.packageName).append(";\n\n");
            }
            sb.append("public final class ").append(generatedClassName(s)).append(" {\n");
            for (MethodIr m : s.methods) {
                sb.append(generateMethod(s, m, problems));
            }
            sb.append("}\n");
            String dir = s.packageName.isEmpty() ? "" : s.packageName.replace('.', '/') + "/";
            out.add(new SourceFile(dir + generatedClassName(s) + ".java", sb.toString()));
        }
        return out;
    }

    protected String generateMethod(StructIr s, MethodIr m, ProblemCollector problems) {
        BlockStmt expanded = expandBody(s, m, problems);
        String params = flattenParams(s, m);
        return "    public static " + m.returnType() + " " + s.name + "_" + m.name()
                + "(" + params + ") " + expanded + "\n";
    }

    protected String flattenParams(StructIr s, MethodIr m) {
        StringBuilder params = new StringBuilder();
        List<String[]> all = new ArrayList<>();
        for (FieldIr f : s.fields) {
            all.add(new String[]{f.type, s.name + "_" + f.name});
        }
        for (var p : m.params()) {
            all.add(new String[]{p.type(), p.name()});
        }
        for (String[] tp : all) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append(tp[0]).append(" ").append(tp[1]);
        }
        return params.toString();
    }

    protected BlockStmt expandBody(StructIr s, MethodIr m, ProblemCollector problems) {
        BlockStmt normalized;
        try {
            normalized = StaticJavaParser.parseBlock(m.normalizedBody());
        } catch (ParseProblemException e) {
            throw new IllegalStateException("failed to reparse normalized body of " + s.name + "." + m.name(), e);
        }
        BodyExpander expander = createBodyExpander(s, generatedClassName(s), problems);
        return (BlockStmt) expander.visit(normalized, null);
    }

    protected BodyExpander createBodyExpander(StructIr s, String genName, ProblemCollector problems) {
        return new BodyExpander(s, genName, problems);
    }

    protected static String placeholderField(Expression e) {
        if (e instanceof NameExpr n && n.getNameAsString().startsWith(StructFrontend.FIELD_PLACEHOLDER)) {
            return n.getNameAsString().substring(StructFrontend.FIELD_PLACEHOLDER.length());
        }
        return null;
    }

    protected static class BodyExpander extends ModifierVisitor<Void> {
        protected final StructIr s;
        protected final String genName;
        protected final ProblemCollector problems;

        protected BodyExpander(StructIr s, String genName, ProblemCollector problems) {
            this.s = s;
            this.genName = genName;
            this.problems = problems;
        }

        @Override
        public Node visit(AssignExpr n, Void arg) {
            checkScalarWrite(n.getTarget());
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(UnaryExpr n, Void arg) {
            if (isIncDec(n.getOperator())) {
                checkScalarWrite(n.getExpression());
            }
            return (Node) super.visit(n, arg);
        }

        protected void checkScalarWrite(Expression target) {
            String fieldName = placeholderField(target);
            if (fieldName != null) {
                FieldIr f = s.field(fieldName);
                if (f != null && f.scalar) {
                    problems.error("<generated:" + genName + ">", 0,
                            "flatten backend cannot persist mutation of scalar field '" + fieldName
                                    + "' in struct " + s.name + " (use the soa backend, or the ssa backend)");
                }
            }
        }

        @Override
        public Node visit(NameExpr n, Void arg) {
            String fieldName = placeholderField(n);
            if (fieldName != null) {
                return new NameExpr(s.name + "_" + fieldName);
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            boolean thisScope = n.getScope().map(sc -> sc instanceof NameExpr ne
                    && ne.getNameAsString().equals(StructFrontend.THIS_PLACEHOLDER)).orElse(false);
            if (thisScope) {
                MethodIr target = s.method(n.getNameAsString());
                if (target == null) {
                    problems.error("<generated:" + genName + ">", 0,
                            "unknown method '" + n.getNameAsString() + "' called inside struct " + s.name);
                    return (Node) super.visit(n, arg);
                }
                return expandThisCall(n, target);
            }
            return (Node) super.visit(n, arg);
        }

        protected MethodCallExpr expandThisCall(MethodCallExpr n, MethodIr target) {
            List<Expression> args = new ArrayList<>();
            for (FieldIr f : s.fields) {
                args.add(new NameExpr(s.name + "_" + f.name));
            }
            for (Expression a : n.getArguments()) {
                args.add((Expression) a.accept(this, null));
            }
            n.setScope(new NameExpr(genName));
            n.setName(s.name + "_" + target.name());
            n.setArguments(new NodeList<>(args));
            return n;
        }
    }
}
