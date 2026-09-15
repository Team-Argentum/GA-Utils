package dev.team_argentum.ga_utils.core;

public record Diagnostic(Severity severity, String path, int line, String message) {
    @Override
    public String toString() {
        return "[" + severity + "] " + path + (line > 0 ? ":" + line : "") + " " + message;
    }
}
