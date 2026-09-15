package dev.team_argentum.ga_utils.struct;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.Processor;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;
import dev.team_argentum.ga_utils.struct.backend.BackendRegistry;
import dev.team_argentum.ga_utils.struct.backend.DispatchCallerBackend;
import dev.team_argentum.ga_utils.struct.backend.StructBackend;
import dev.team_argentum.ga_utils.struct.frontend.ParsedFile;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.FieldIr;
import dev.team_argentum.ga_utils.struct.ir.MethodIr;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructExpandProcessor implements Processor {

    @Override
    public String id() {
        return "struct-expand";
    }

    @Override
    public TransformResult process(List<SourceFile> inputs, Map<String, String> options) {
        ProblemCollector problems = new ProblemCollector();

        List<ParsedFile> parsed = new StructFrontend().parse(inputs, problems);
        Map<String, StructIr> catalog = StructCatalogs.build(parsed, problems);

        List<SourceFile> outputs = new ArrayList<>();
        String defaultBackend = options.getOrDefault("backend", "flatten");
        StructBackend callerBackend = new DispatchCallerBackend(defaultBackend);
        if (!problems.hasErrors()) {
            outputs.addAll(StructGeneration.generate(new ArrayList<>(catalog.values()), options, defaultBackend, problems));
            for (ParsedFile pf : parsed) {
                for (ClassOrInterfaceDeclaration c : List.copyOf(pf.cu().findAll(ClassOrInterfaceDeclaration.class))) {
                    if (StructFrontend.isStruct(c)) {
                        c.remove();
                    }
                }
                callerBackend.rewriteCallers(pf.cu(), catalog, problems, pf.path());
                if (!problems.hasErrorsFor(pf.path())) {
                    for (ClassOrInterfaceType t : pf.cu().findAll(ClassOrInterfaceType.class)) {
                        if (catalog.containsKey(t.getNameAsString())) {
                            problems.error(pf.path(), StructFrontend.line(t),
                                    "unresolved reference to @Struct type '" + t.getNameAsString() + "' remains after rewrite");
                        }
                    }
                }
                if (!pf.cu().getTypes().isEmpty()) {
                    outputs.add(new SourceFile(pf.path(), pf.cu().toString()));
                }
            }
        }
        if (problems.hasErrors()) {
            outputs.clear();
        }
        return new TransformResult(outputs, problems.diagnostics());
    }
}
