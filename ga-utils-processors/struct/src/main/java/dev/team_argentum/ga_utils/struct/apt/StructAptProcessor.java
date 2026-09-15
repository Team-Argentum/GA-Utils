package dev.team_argentum.ga_utils.struct.apt;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import dev.team_argentum.ga_utils.api.Struct;
import dev.team_argentum.ga_utils.core.Diagnostic;
import dev.team_argentum.ga_utils.core.ProblemCollector;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.struct.StructCatalogs;
import dev.team_argentum.ga_utils.struct.StructGeneration;
import dev.team_argentum.ga_utils.struct.frontend.ParsedFile;
import dev.team_argentum.ga_utils.struct.frontend.StructFrontend;
import dev.team_argentum.ga_utils.struct.ir.StructIr;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("dev.team_argentum.ga_utils.api.Struct")
@SupportedOptions({"gautils.backend", "gautils.capacity"})
public final class StructAptProcessor extends AbstractProcessor {

    private Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(Struct.class);
        if (annotated.isEmpty()) {
            return true;
        }

        String backendName = processingEnv.getOptions().getOrDefault("gautils.backend", "flatten");
        ProblemCollector problems = new ProblemCollector();

        List<SourceFile> sources = new ArrayList<>();
        for (Element e : annotated) {
            try {
                var path = trees.getPath(e);
                if (path == null) {
                    problems.error(elementName(e), 0, "cannot resolve source of @Struct element: " + elementName(e));
                    continue;
                }
                CompilationUnitTree cu = path.getCompilationUnit();
                CharSequence content = cu.getSourceFile().getCharContent(true);
                sources.add(new SourceFile(cu.getSourceFile().getName().replace('\\', '/'), content.toString()));
            } catch (IOException ex) {
                problems.error(elementName(e), 0, "cannot read source of @Struct element " + elementName(e) + ": " + ex.getMessage());
            }
        }

        List<ParsedFile> parsed = new StructFrontend().parse(sources, problems);
        Map<String, StructIr> catalog = StructCatalogs.build(parsed, problems);

        if (!problems.hasErrors()) {
            Map<String, String> options = new HashMap<>();
            options.put("backend", backendName);
            String capacity = processingEnv.getOptions().get("gautils.capacity");
            if (capacity != null) {
                options.put("capacity", capacity);
            }
            List<SourceFile> generated = StructGeneration.generate(new ArrayList<>(catalog.values()), options, backendName, problems);
            if (!problems.hasErrors()) {
                for (SourceFile f : generated) {
                    writeGenerated(f, problems);
                }
            }
        }

        report(problems);
        return true;
    }

    private void writeGenerated(SourceFile f, ProblemCollector problems) {
        int slash = f.path().lastIndexOf('/');
        int dot = f.path().lastIndexOf('.');
        String className = f.path().substring(slash + 1, dot);
        String pkg = slash < 0 ? "" : f.path().substring(0, slash).replace('/', '.');
        String fqn = pkg.isEmpty() ? className : pkg + "." + className;
        try {
            JavaFileObject jfo = processingEnv.getFiler().createSourceFile(fqn);
            try (Writer w = jfo.openWriter()) {
                w.write(f.content());
            }
        } catch (IOException e) {
            problems.error(f.path(), 0, "failed to write generated source " + fqn + ": " + e.getMessage());
        }
    }

    private void report(ProblemCollector problems) {
        if (problems.diagnostics().isEmpty()) {
            return;
        }
        Messager messager = processingEnv.getMessager();
        for (Diagnostic d : problems.diagnostics()) {
            javax.tools.Diagnostic.Kind kind = switch (d.severity()) {
                case ERROR -> javax.tools.Diagnostic.Kind.ERROR;
                case WARNING -> javax.tools.Diagnostic.Kind.WARNING;
                case INFO -> javax.tools.Diagnostic.Kind.NOTE;
            };
            messager.printMessage(kind, d.toString());
        }
    }

    private static String elementName(Element e) {
        return String.valueOf(e);
    }
}
