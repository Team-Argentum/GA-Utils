package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SoaBackend extends AbstractJavaParserBackend {

    public static final String ID_SUFFIX = "Id";
    public static final String POOL_ID_PARAM = "__ga_id";

    @Override
    public String name() {
        return "soa";
    }

    @Override
    public String generatedClassName(StructIr s) {
        return s.name + "Pool";
    }

    @Override
    protected List<String[]> expandParam(StructIr s, String varName) {
        return java.util.List.<String[]>of(new String[]{"int", varName + ID_SUFFIX});
    }

    @Override
    protected List<Expression> expandVar(StructIr s, String varName) {
        return List.of(new NameExpr(varName + ID_SUFFIX));
    }

    @Override
    protected Expression fieldExpr(StructIr s, String varName, FieldIr f) {
        return new ArrayAccessExpr(
                new FieldAccessExpr(new NameExpr(generatedClassName(s)), f.name),
                new NameExpr(varName + ID_SUFFIX));
    }

    @Override
    protected Expression bareStructVarRef(NameExpr n, StructIr s, String varName, ProblemCollector problems, String path) {
        return new NameExpr(varName + ID_SUFFIX);
    }

    @Override
    public Expression finishStructCall(MethodCallExpr call, StructIr s, MethodIr m, List<Expression> args) {
        call.setScope(new NameExpr(generatedClassName(s)));
        call.setArguments(new NodeList<>(args));
        return call;
    }

    @Override
    protected boolean supportsStructLocals(StructIr s) {
        return true;
    }

    @Override
    protected String rewrittenLocalVarName(StructIr s, String varName) {
        return varName + ID_SUFFIX;
    }

    @Override
    protected com.github.javaparser.ast.type.Type structLocalType(StructIr s) {
        return new PrimitiveType(PrimitiveType.Primitive.INT);
    }

    @Override
    protected Expression structCreationExpr(StructIr s) {
        return new MethodCallExpr(new NameExpr(generatedClassName(s)), "alloc");
    }

    @Override
    public List<SourceFile> generateStructSources(List<StructIr> structs, Map<String, String> options, ProblemCollector problems) {
        int capacity;
        try {
            capacity = Integer.parseInt(options.getOrDefault("capacity", "10000"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid 'capacity' option for soa backend");
        }
        List<SourceFile> out = new ArrayList<>();
        for (StructIr s : structs) {
            StringBuilder sb = new StringBuilder();
            if (!s.packageName.isEmpty()) {
                sb.append("package ").append(s.packageName).append(";\n\n");
            }
            sb.append("public final class ").append(generatedClassName(s)).append(" {\n");
            sb.append("    public static final int CAPACITY = ").append(capacity).append(";\n\n");
            for (FieldIr f : s.fields) {
                sb.append(columnDecl(f));
            }
            sb.append("    public static int count = 0;\n\n");
            sb.append("    public static int alloc() {\n");
            sb.append("        if (count >= CAPACITY) {\n");
            sb.append("            throw new IllegalStateException(\"pool overflow: ").append(generatedClassName(s)).append("\");\n");
            sb.append("        }\n");
            sb.append("        int id = count;\n");
            sb.append("        count++;\n");
            sb.append("        return id;\n");
            sb.append("    }\n");
            for (MethodIr m : s.methods) {
                sb.append(generateMethod(s, m, problems));
            }
            sb.append("}\n");
            String dir = s.packageName.isEmpty() ? "" : s.packageName.replace('.', '/') + "/";
            out.add(new SourceFile(dir + generatedClassName(s) + ".java", sb.toString()));
        }
        return out;
    }

    private static String columnDecl(FieldIr f) {
        String base = f.type.replace("[]", "").trim();
        int dims = (f.type.length() - f.type.replace("[]", "").length()) / 2;
        StringBuilder colType = new StringBuilder(base);
        StringBuilder init = new StringBuilder("new ").append(base).append("[CAPACITY]");
        for (int i = 0; i < dims + 1; i++) {
            colType.append("[]");
        }
        for (int i = 0; i < dims; i++) {
            init.append("[]");
        }
        return "    public static " + colType + " " + f.name + " = " + init + ";\n";
    }

    private String generateMethod(StructIr s, MethodIr m, ProblemCollector problems) {
        BlockStmt expanded = expandBody(s, m, problems);
        StringBuilder params = new StringBuilder("int " + POOL_ID_PARAM);
        for (var p : m.params()) {
            params.append(", ").append(p.type()).append(" ").append(p.name());
        }
        return "    public static " + m.returnType() + " " + m.name()
                + "(" + params + ") " + expanded + "\n";
    }

    private BlockStmt expandBody(StructIr s, MethodIr m, ProblemCollector problems) {
        BlockStmt normalized;
        try {
            normalized = StaticJavaParser.parseBlock(m.normalizedBody());
        } catch (ParseProblemException e) {
            throw new IllegalStateException("failed to reparse normalized body of " + s.name + "." + m.name(), e);
        }
        BodyExpander expander = new BodyExpander(s, generatedClassName(s), problems);
        return (BlockStmt) expander.visit(normalized, null);
    }

    private static final class BodyExpander extends ModifierVisitor<Void> {
        private final StructIr s;
        private final String genName;
        private final ProblemCollector problems;

        private BodyExpander(StructIr s, String genName, ProblemCollector problems) {
            this.s = s;
            this.genName = genName;
            this.problems = problems;
        }

        @Override
        public Node visit(NameExpr n, Void arg) {
            if (n.getNameAsString().startsWith(StructFrontend.FIELD_PLACEHOLDER)) {
                String fieldName = n.getNameAsString().substring(StructFrontend.FIELD_PLACEHOLDER.length());
                return new ArrayAccessExpr(new NameExpr(fieldName), new NameExpr(POOL_ID_PARAM));
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
                List<Expression> args = new ArrayList<>();
                args.add(new NameExpr(POOL_ID_PARAM));
                for (Expression a : n.getArguments()) {
                    args.add((Expression) a.accept(this, null));
                }
                n.setScope(null);
                n.setArguments(new NodeList<>(args));
                return n;
            }
            return (Node) super.visit(n, arg);
        }
    }
}
