package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.Diagnostic;
import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CliMain {

    public static void main(String[] args) throws IOException {
        String backend = "flatten";
        Path out = Path.of("build", "gautils-generated");
        List<Path> inputs = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--backend=")) {
                backend = a.substring("--backend=".length());
            } else if (a.startsWith("--out=")) {
                out = Path.of(a.substring("--out=".length()));
            } else {
                inputs.add(Path.of(a));
            }
        }
        if (inputs.isEmpty()) {
            System.err.println("usage: CliMain [--backend=flatten|soa] [--out=DIR] <files or dirs...>");
            System.exit(2);
        }

        List<SourceFile> files = new ArrayList<>();
        for (Path p : inputs) {
            if (!Files.exists(p)) {
                System.err.println("skipping missing input path: " + p);
                continue;
            }
            if (Files.isDirectory(p)) {
                Path root = p.toAbsolutePath().normalize();
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(f -> f.toString().endsWith(".java"))
                            .forEach(f -> files.add(new SourceFile(root.relativize(f).toString().replace('\\', '/'), SourceFile.read(f).content())));
                }
            } else {
                files.add(new SourceFile(p.getFileName().toString(), SourceFile.read(p).content()));
            }
        }

        TransformResult result = new StructExpandProcessor().process(files, Map.of("backend", backend));

        for (Diagnostic d : result.diagnostics()) {
            System.out.println(d);
        }
        for (SourceFile f : result.outputs()) {
            f.writeTo(out);
        }
        System.out.println("written " + result.outputs().size() + " file(s) to " + out);
        if (result.hasErrors()) {
            System.exit(1);
        }
    }
}
