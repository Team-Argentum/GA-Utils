package dev.team_argentum.ga_utils.struct.backend;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractJavaParserBackend implements StructBackend {

    public abstract String generatedClassName(StructIr s);

    protected abstract List<String[]> expandParam(StructIr s, String varName);

    protected abstract List<Expression> expandVar(StructIr s, String varName);

    protected abstract Expression fieldExpr(StructIr s, String varName, FieldIr f);

    protected abstract Expression bareStructVarRef(NameExpr n, StructIr s, String varName, ProblemCollector problems, String path);

    protected abstract Expression finishStructCall(MethodCallExpr call, StructIr s, MethodIr m, List<Expression> args);

    protected boolean supportsStructLocals(StructIr s) {
        return false;
    }

    protected String rewrittenLocalVarName(StructIr s, String varName) {
        return varName;
    }

    protected Type structLocalType(StructIr s) {
        return null;
    }

    protected Expression structCreationExpr(StructIr s) {
        return null;
    }

    protected void fieldWriteCheck(Node fieldAccessNode, StructIr s, FieldIr f, boolean isLocalVar,
                                   ProblemCollector problems, String path) {
    }

    protected CallerRewriter createCallerRewriter(Map<String, StructIr> catalog, ProblemCollector problems, String path) {
        return new CallerRewriter(this, catalog, problems, path);
    }

    protected Node rewriteStructCallStatement(CallerRewriter rewriter, com.github.javaparser.ast.stmt.ExpressionStmt stmt,
                                              MethodCallExpr call, StructIr s, MethodIr m, NameExpr scopeName) {
        return null;
    }

    @Override
    public void rewriteCallers(CompilationUnit cu, Map<String, StructIr> catalog, ProblemCollector problems, String path) {
        CallerRewriter rewriter = createCallerRewriter(catalog, problems, path);
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (StructFrontend.isStruct(type)) {
                continue;
            }
            for (MethodDeclaration md : type.getMethods()) {
                rewriter.rewriteMethod(md);
            }
        }
    }

    protected String displayName(StructIr s) {
        return name();
    }

    protected boolean supportsFieldLocalExpansion(StructIr s) {
        return false;
    }

    protected Node expandFieldLocalDeclaration(CallerRewriter rewriter, com.github.javaparser.ast.stmt.ExpressionStmt stmt,
                                               com.github.javaparser.ast.expr.VariableDeclarationExpr decl, StructIr s) {
        return null;
    }

    protected static class CallerRewriter extends ModifierVisitor<Void> {
        protected final AbstractJavaParserBackend backend;
        protected final Map<String, StructIr> catalog;
        protected final ProblemCollector problems;
        protected final String path;
        protected final Map<String, StructIr> vars = new HashMap<>();
        protected final Set<String> structLocals = new HashSet<>();

        protected CallerRewriter(AbstractJavaParserBackend backend, Map<String, StructIr> catalog,
                                 ProblemCollector problems, String path) {
            this.backend = backend;
            this.catalog = catalog;
            this.problems = problems;
            this.path = path;
        }

        public void rewriteMethod(MethodDeclaration md) {
            vars.clear();
            structLocals.clear();
            StructIr returnType = structByType(md.getType().asString());
            if (returnType != null) {
                problems.error(path, StructFrontend.line(md),
                        "returning a @Struct value is not supported: " + md.getNameAsString());
            }
            List<Parameter> newParams = new ArrayList<>();
            boolean changed = false;
            for (Parameter p : md.getParameters()) {
                StructIr s = structByType(p.getType().asString());
                if (s != null) {
                    changed = true;
                    vars.put(p.getNameAsString(), s);
                    for (String[] tp : backend.expandParam(s, p.getNameAsString())) {
                        newParams.add(new Parameter(StaticJavaParser.parseType(tp[0]), tp[1]));
                    }
                } else {
                    newParams.add(p);
                }
            }
            if (changed) {
                md.setParameters(new NodeList<>(newParams));
            }
            md.getBody().ifPresent(b -> b.accept(this, null));
        }

        protected StructIr structByType(String type) {
            String t = type.replace("[]", "").trim();
            if (catalog.containsKey(t)) {
                return catalog.get(t);
            }
            int dot = t.lastIndexOf('.');
            if (dot >= 0) {
                return catalog.get(t.substring(dot + 1));
            }
            return null;
        }

        @Override
        public Node visit(ExpressionStmt n, Void arg) {
            if (n.getExpression() instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr decl
                    && !decl.getVariables().isEmpty()) {
                StructIr s = structByType(decl.getVariables().get(0).getType().asString());
                boolean allSameStruct = decl.getVariables().stream()
                        .allMatch(d -> structByType(d.getType().asString()) == s);
                if (s != null && allSameStruct && backend.supportsFieldLocalExpansion(s)) {
                    Node replacement = backend.expandFieldLocalDeclaration(this, n, decl, s);
                    if (replacement != null) {
                        return replacement;
                    }
                }
            }
            if (n.getExpression() instanceof MethodCallExpr call
                    && call.getScope().isPresent()
                    && call.getScope().get() instanceof NameExpr scopeName) {
                StructIr s = vars.get(scopeName.getNameAsString());
                if (s != null) {
                    MethodIr m = s.method(call.getNameAsString());
                    if (m != null) {
                        Node replacement = backend.rewriteStructCallStatement(this, n, call, s, m, scopeName);
                        if (replacement != null) {
                            return replacement;
                        }
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            if (n.getScope().isPresent() && n.getScope().get() instanceof NameExpr scopeName) {
                StructIr s = vars.get(scopeName.getNameAsString());
                if (s != null) {
                    MethodIr m = s.method(n.getNameAsString());
                    if (m == null) {
                        problems.error(path, StructFrontend.line(n),
                                "unknown method '" + n.getNameAsString() + "' on struct " + s.name);
                        return n;
                    }
                    List<Expression> args = new ArrayList<>(backend.expandVar(s, scopeName.getNameAsString()));
                    for (Expression a : n.getArguments()) {
                        args.addAll(rewriteCallArg(a));
                    }
                    return backend.finishStructCall(n, s, m, args);
                }
            }
            List<Expression> newArgs = new ArrayList<>();
            for (Expression a : n.getArguments()) {
                newArgs.addAll(rewriteCallArg(a));
            }
            n.getScope().ifPresent(sc -> n.setScope((Expression) sc.accept(this, null)));
            n.setArguments(new NodeList<>(newArgs));
            return n;
        }

        protected List<Expression> rewriteCallArg(Expression a) {
            if (a instanceof NameExpr ne && vars.containsKey(ne.getNameAsString())) {
                return backend.expandVar(vars.get(ne.getNameAsString()), ne.getNameAsString());
            }
            com.github.javaparser.ast.visitor.Visitable r = a.accept(this, null);
            return List.of(r instanceof Expression e ? e : a);
        }

        @Override
        public Node visit(FieldAccessExpr n, Void arg) {
            if (n.getScope() instanceof NameExpr scopeName) {
                StructIr s = vars.get(scopeName.getNameAsString());
                if (s != null) {
                    FieldIr f = s.field(n.getNameAsString());
                    if (f == null) {
                        problems.error(path, StructFrontend.line(n),
                                "unknown field '" + n.getNameAsString() + "' on struct " + s.name);
                        return n;
                    }
                    backend.fieldWriteCheck(n, s, f, structLocals.contains(scopeName.getNameAsString()), problems, path);
                    return backend.fieldExpr(s, scopeName.getNameAsString(), f);
                }
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(NameExpr n, Void arg) {
            StructIr s = vars.get(n.getNameAsString());
            if (s != null) {
                return backend.bareStructVarRef(n, s, n.getNameAsString(), problems, path);
            }
            return (Node) super.visit(n, arg);
        }

        @Override
        public Node visit(VariableDeclarator n, Void arg) {
            StructIr s = structByType(n.getType().asString());
            if (s == null) {
                return (Node) super.visit(n, arg);
            }
            String varName = n.getNameAsString();
            if (!backend.supportsStructLocals(s)) {
                problems.error(path, StructFrontend.line(n),
                        "backend '" + backend.displayName(s) + "' does not support local variables of @Struct type '"
                                + s.name + "': " + varName + " (locals with 'new' are supported by the 'soa' backend)");
                return n;
            }
            vars.put(varName, s);
            if (n.getInitializer().isPresent()) {
                n.setInitializer((Expression) n.getInitializer().get().accept(this, null));
            }
            n.setType(backend.structLocalType(s));
            n.setName(backend.rewrittenLocalVarName(s, varName));
            return n;
        }

        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            StructIr s = structByType(n.getType().asString());
            if (s != null) {
                if (!backend.supportsStructLocals(s)) {
                    problems.error(path, StructFrontend.line(n),
                            "backend '" + backend.displayName(s) + "' cannot instantiate @Struct '"
                                    + s.name + "' with 'new' (supported by the 'soa' backend)");
                    return n;
                }
                return backend.structCreationExpr(s);
            }
            return (Node) super.visit(n, arg);
        }
    }
}
