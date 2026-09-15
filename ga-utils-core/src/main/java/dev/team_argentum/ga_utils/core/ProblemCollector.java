package dev.team_argentum.ga_utils.core;

import java.util.ArrayList;
import java.util.List;

public final class ProblemCollector {
    private final List<Diagnostic> items = new ArrayList<>();

    public void error(String path, int line, String message) {
        items.add(new Diagnostic(Severity.ERROR, path, line, message));
    }

    public void warning(String path, int line, String message) {
        items.add(new Diagnostic(Severity.WARNING, path, line, message));
    }

    public void info(String path, int line, String message) {
        items.add(new Diagnostic(Severity.INFO, path, line, message));
    }

    public boolean hasErrors() {
        return items.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public boolean hasErrorsFor(String path) {
        return items.stream().anyMatch(d -> d.severity() == Severity.ERROR && d.path().equals(path));
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(items);
    }
}
