package dev.team_argentum.ga_utils.struct.frontend;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.ParamIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StructFrontend {
    public static final String FIELD_PLACEHOLDER = "__ga_field_";
    public static final String THIS_PLACEHOLDER = "__ga_this";

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public List<ParsedFile> parse(List<SourceFile> inputs, ProblemCollector problems) {
        List<ParsedFile> out = new ArrayList<>();
        for (SourceFile f : inputs) {
            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(f.content());
            } catch (ParseProblemException e) {
                problems.error(f.path(), 0, "parse failed: " + e.getMessage());
                continue;
            }
            List<StructIr> structs = new ArrayList<>();
            for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isStruct(c)) {
                    StructIr ir = buildStruct(f.path(), c, problems);
                    if (ir != null) {
                        structs.add(ir);
                    }
                }
            }
            out.add(new ParsedFile(f.path(), cu, structs));
        }
        return out;
    }

    public static boolean isStruct(ClassOrInterfaceDeclaration c) {
        return c.getAnnotations().stream().anyMatch(a -> a.getNameAsString().endsWith("Struct"));
    }

    private StructIr buildStruct(String path, ClassOrInterfaceDeclaration c, ProblemCollector problems) {
        String pkg = c.findCompilationUnit()
                .flatMap(cu -> cu.getPackageDeclaration())
                .map(p -> p.getNameAsString())
                .orElse("");
        String backendName = readBackendAnnotation(c);

        List<FieldIr> fields = new ArrayList<>();
        for (FieldDeclaration fd : c.getFields()) {
            if (fd.isStatic()) {
                problems.error(path, line(fd), "static fields are not supported in @Struct: " + c.getNameAsString());
                continue;
            }
            for (VariableDeclarator v : fd.getVariables()) {
                fields.add(new FieldIr(v.getNameAsString(), v.getType().asString()));
            }
        }

        List<MethodIr> methods = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();
        for (FieldIr f : fields) {
            fieldNames.add(f.name);
        }
        for (MethodDeclaration m : c.getMethods()) {
            if (m.isStatic()) {
                problems.error(path, line(m), "static methods are not supported in @Struct: " + c.getNameAsString() + "." + m.getNameAsString());
                continue;
            }
            if (m.getBody().isEmpty()) {
                problems.error(path, line(m), "abstract methods are not supported in @Struct: " + c.getNameAsString() + "." + m.getNameAsString());
                continue;
            }
            List<ParamIr> params = new ArrayList<>();
            for (Parameter p : m.getParameters()) {
                params.add(new ParamIr(p.getNameAsString(), p.getType().asString()));
            }
            String normalized = normalizeBody(m.getBody().get(), fieldNames).toString();
            methods.add(new MethodIr(m.getNameAsString(), m.getType().asString(), List.copyOf(params), normalized));
        }

        for (MethodIr m : methods) {
            long dup = methods.stream().filter(x -> x.name().equals(m.name())).count();
            if (dup > 1) {
                problems.error(path, 0, "method overloads are not supported in @Struct: " + c.getNameAsString() + "." + m.name());
                return null;
            }
        }

        return new StructIr(pkg, c.getNameAsString(), backendName, List.copyOf(fields), List.copyOf(methods));
    }

    public static String readBackendAnnotation(ClassOrInterfaceDeclaration c) {
        for (com.github.javaparser.ast.expr.AnnotationExpr a : c.getAnnotations()) {
            if (!a.getNameAsString().endsWith("Struct") || !(a instanceof com.github.javaparser.ast.expr.NormalAnnotationExpr na)) {
                continue;
            }
            for (com.github.javaparser.ast.expr.MemberValuePair pair : na.getPairs()) {
                if (pair.getNameAsString().equals("backend")) {
                    String value = pair.getValue().toString();
                    String last = value.substring(value.lastIndexOf('.') + 1).replace("\"", "").trim();
                    return last.toLowerCase(java.util.Locale.ROOT);
                }
            }
            return null;
        }
        return null;
    }

    public static BlockStmt normalizeBody(BlockStmt body, Set<String> fieldNames) {
        BlockStmt copy = body.clone();
        Set<String> locals = new HashSet<>();
        copy.findAll(VariableDeclarator.class).forEach(v -> locals.add(v.getNameAsString()));

        for (NameExpr n : copy.findAll(NameExpr.class)) {
            String name = n.getNameAsString();
            if (fieldNames.contains(name) && !locals.contains(name)) {
                n.setName(FIELD_PLACEHOLDER + name);
            }
        }
        for (FieldAccessExpr fa : List.copyOf(copy.findAll(FieldAccessExpr.class))) {
            if (fa.getScope() instanceof ThisExpr && fieldNames.contains(fa.getNameAsString())) {
                fa.replace(new NameExpr(FIELD_PLACEHOLDER + fa.getNameAsString()));
            }
        }
        for (MethodCallExpr mc : copy.findAll(MethodCallExpr.class)) {
            boolean noScope = !mc.getScope().isPresent();
            boolean thisScope = mc.getScope().map(s -> s instanceof ThisExpr).orElse(false);
            if (noScope || thisScope) {
                mc.setScope(new NameExpr(THIS_PLACEHOLDER));
            }
        }
        return copy;
    }

    public static int line(com.github.javaparser.ast.Node n) {
        return n.getRange().map(r -> r.begin.line).orElse(0);
    }
}
