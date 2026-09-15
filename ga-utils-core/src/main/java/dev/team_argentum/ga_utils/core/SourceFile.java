package dev.team_argentum.ga_utils.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;

public record SourceFile(String path, String content) {
    public static SourceFile read(Path file) {
        try {
            return new SourceFile(file.toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeTo(Path root) {
        Path target = root.resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
