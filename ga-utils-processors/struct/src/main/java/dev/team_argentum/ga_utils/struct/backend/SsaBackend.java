package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SsaBackend extends FlattenBackend {

    @Override
    public String name() {
        return "ssa";
    }

    @Override
    protected void fieldWriteCheck(Node fieldAccessNode, StructIr s, FieldIr f, ProblemCollector problems, String path) {
    }

    @Override
    protected BodyExpander createBodyExpander(StructIr s, String genName, ProblemCollector problems) {
        return new SsaBodyExpander(s, genName, problems);
    }

    @Override
    protected Node rewriteStructCallStatement(CallerRewriter rewriter, ExpressionStmt stmt,
                                              MethodCallExpr call, StructIr s, MethodIr m, NameExpr scopeName) {
        Set<String> mutated = mutatedScalarFields(s, m);
        if (mutated.size() != 1) {
            return null;
        }
        List<Expression> args = new ArrayList<>(expandVar(s, scopeName.getNameAsString()));
        for (Expression a : call.getArguments()) {
            args.addAll(rewriter.rewriteCallArg(a));
        }
        MethodCallExpr rewritten = (MethodCallExpr) finishStructCall(call, s, m, args);
        String field = mutated.iterator().next();
        stmt.setExpression(new AssignExpr(fieldExpr(s, scopeName.getNameAsString(), s.field(field)),
                rewritten, AssignExpr.Operator.ASSIGN));
        return stmt;
    }

    @Override
    protected String generateMethod(StructIr s, MethodIr m, ProblemCollector problems) {
        Set<String> mutated = mutatedScalarFields(s, m);
        if (mutated.isEmpty()) {
            return super.generateMethod(s, m, problems);
        }
        String gen = "<generated:" + generatedClassName(s) + ">";
        if (mutated.size() != 1) {
            problems.error(gen, 0, "ssa backend: method " + s.name + "." + m.name() + " mutates "
                    + mutated.size() + " scalar fields; only single-field mutation is supported");
            return "";
        }
        if (!m.returnType().equals("void")) {
            problems.error(gen, 0, "ssa backend: method " + s.name + "." + m.name()
                    + " returns a value and mutates fields; this is not supported");
            return "";
        }
        if (hasBareReturn(s, m)) {
            problems.error(gen, 0, "ssa backend: method " + s.name + "." + m.name()
                    + " contains a bare 'return;' and cannot be converted to return the mutated field");
            return "";
        }
        String field = mutated.iterator().next();
        BlockStmt expanded = expandBody(s, m, problems);
        expanded.addStatement(new ReturnStmt(new NameExpr(s.name + "_" + field)));
        return "    public static " + s.field(field).type + " " + s.name + "_" + m.name()
                + "(" + flattenParams(s, m) + ") " + expanded + "\n";
    }

    private static boolean hasBareReturn(StructIr s, MethodIr m) {
        BlockStmt body;
        try {
            body = StaticJavaParser.parseBlock(m.normalizedBody());
        } catch (ParseProblemException e) {
            throw new IllegalStateException("failed to reparse normalized body of " + s.name + "." + m.name(), e);
        }
        return body.findAll(ReturnStmt.class).stream().anyMatch(r -> r.getExpression().isEmpty());
    }

    private static final class SsaBodyExpander extends BodyExpander {

        private SsaBodyExpander(StructIr s, String genName, ProblemCollector problems) {
            super(s, genName, problems);
        }

        @Override
        protected void checkScalarWrite(Expression target) {
        }

        @Override
        public Node visit(ExpressionStmt n, Void arg) {
            if (n.getExpression() instanceof MethodCallExpr call
                    && call.getScope().map(sc -> sc instanceof NameExpr ne
                    && ne.getNameAsString().equals(StructFrontend.THIS_PLACEHOLDER)).orElse(false)) {
                MethodIr target = s.method(call.getNameAsString());
                if (target != null) {
                    Set<String> mutated = mutatedScalarFields(s, target);
                    if (mutated.size() == 1) {
                        MethodCallExpr expanded = expandThisCall(call, target);
                        String field = mutated.iterator().next();
                        n.setExpression(new AssignExpr(new NameExpr(s.name + "_" + field), expanded, AssignExpr.Operator.ASSIGN));
                        return n;
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            boolean thisScope = n.getScope().map(sc -> sc instanceof NameExpr ne
                    && ne.getNameAsString().equals(StructFrontend.THIS_PLACEHOLDER)).orElse(false);
            if (thisScope) {
                MethodIr target = s.method(n.getNameAsString());
                if (target != null && !mutatedScalarFields(s, target).isEmpty()
                        && !(n.getParentNode().orElse(null) instanceof ExpressionStmt)) {
                    problems.error("<generated:" + genName + ">", 0,
                            "ssa backend: mutating method '" + target.name() + "' is used inside an expression in struct "
                                    + s.name + "; call it as a separate statement");
                }
            }
            return (Node) super.visit(n, arg);
        }
    }
}
