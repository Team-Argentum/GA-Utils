package dev.team_argentum.ga_utils.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TransformResult(List<SourceFile> outputs, List<Diagnostic> diagnostics) {
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public Optional<SourceFile> output(String path) {
        return outputs.stream().filter(f -> f.path().equals(path)).findFirst();
    }
}
