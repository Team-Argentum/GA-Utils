package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.List;
import java.util.Map;

public final class DispatchCallerBackend extends AbstractJavaParserBackend {

    private final String defaultBackend;

    public DispatchCallerBackend(String defaultBackend) {
        this.defaultBackend = defaultBackend;
    }

    @Override
    public String name() {
        return "per-struct dispatch";
    }

    @Override
    protected String displayName(StructIr s) {
        return target(s).displayName(s);
    }

    private AbstractJavaParserBackend target(StructIr s) {
        String name = s != null && s.backendName != null ? s.backendName : defaultBackend;
        return (AbstractJavaParserBackend) BackendRegistry.byName(name);
    }

    @Override
    public String generatedClassName(StructIr s) {
        return target(s).generatedClassName(s);
    }

    @Override
    protected List<String[]> expandParam(StructIr s, String varName) {
        return target(s).expandParam(s, varName);
    }

    @Override
    protected List<Expression> expandVar(StructIr s, String varName) {
        return target(s).expandVar(s, varName);
    }

    @Override
    protected Expression fieldExpr(StructIr s, String varName, FieldIr f) {
        return target(s).fieldExpr(s, varName, f);
    }

    @Override
    protected Expression bareStructVarRef(NameExpr n, StructIr s, String varName, ProblemCollector problems, String path) {
        return target(s).bareStructVarRef(n, s, varName, problems, path);
    }

    @Override
    public Expression finishStructCall(MethodCallExpr call, StructIr s, MethodIr m, List<Expression> args) {
        return target(s).finishStructCall(call, s, m, args);
    }

    @Override
    protected Node rewriteStructCallStatement(CallerRewriter rewriter, com.github.javaparser.ast.stmt.ExpressionStmt stmt,
                                              MethodCallExpr call, StructIr s, MethodIr m, NameExpr scopeName) {
        return target(s).rewriteStructCallStatement(rewriter, stmt, call, s, m, scopeName);
    }

    @Override
    protected boolean supportsFieldLocalExpansion(StructIr s) {
        return target(s).supportsFieldLocalExpansion(s);
    }

    @Override
    protected Node expandFieldLocalDeclaration(CallerRewriter rewriter,
                                               com.github.javaparser.ast.stmt.ExpressionStmt stmt,
                                               com.github.javaparser.ast.expr.VariableDeclarationExpr decl,
                                               com.github.javaparser.ast.body.VariableDeclarator d, StructIr s) {
        return target(s).expandFieldLocalDeclaration(rewriter, stmt, decl, d, s);
    }

    @Override
    protected boolean supportsStructLocals(StructIr s) {
        return target(s).supportsStructLocals(s);
    }

    @Override
    protected String rewrittenLocalVarName(StructIr s, String varName) {
        return target(s).rewrittenLocalVarName(s, varName);
    }

    @Override
    protected com.github.javaparser.ast.type.Type structLocalType(StructIr s) {
        return target(s).structLocalType(s);
    }

    @Override
    protected Expression structCreationExpr(StructIr s) {
        return target(s).structCreationExpr(s);
    }

    @Override
    protected void fieldWriteCheck(Node fieldAccessNode, StructIr s, FieldIr f, ProblemCollector problems, String path) {
        target(s).fieldWriteCheck(fieldAccessNode, s, f, problems, path);
    }

    @Override
    public List<SourceFile> generateStructSources(List<StructIr> structs, Map<String, String> options, ProblemCollector problems) {
        throw new UnsupportedOperationException("dispatch backend is for caller rewriting only");
    }
}
