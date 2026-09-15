package dev.team_argentum.ga_utils.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GaUtilsPluginFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void wiresExpansionBeforeCompilation() throws IOException {
        Path toolClasses = compileStubTool();

        write("settings.gradle", "rootProject.name = 'sample'\n");
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'dev.team_argentum.ga-utils'
                }
                gautils {
                    toolProject = ''
                }
                dependencies {
                    gautilsTool files('%s')
                }
                """.formatted(toolClasses.toString().replace('\\', '/')));
        write("src/main/java/testpkg/Game.java", """
                package testpkg;

                public class Game {
                    public static int go() {
                        return MyClass_Dod.foo();
                    }
                }
                """);

        var result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments("compileJava")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava").getOutcome());
        assertTrue(Files.exists(projectDir.resolve("build/gautils/generated-src/main/testpkg/MyClass_Dod.java")),
                result.getOutput());
        assertTrue(Files.exists(projectDir.resolve("build/classes/java/main/testpkg/Game.class")));
    }

    private Path compileStubTool() throws IOException {
        Path srcDir = Files.createTempDirectory("ga-stub-src");
        Path outDir = Files.createTempDirectory("ga-stub-classes");
        Path src = srcDir.resolve("dev/team_argentum/ga_utils/struct/CliMain.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package dev.team_argentum.ga_utils.struct;

                import java.nio.file.Files;
                import java.nio.file.Path;

                public class CliMain {
                    public static void main(String[] args) throws Exception {
                        String out = null;
                        String src = null;
                        for (String a : args) {
                            if (a.startsWith("--out=")) {
                                out = a.substring("--out=".length());
                            } else if (!a.startsWith("--")) {
                                src = a;
                            }
                        }
                        Path root = Path.of(src);
                        final String outPath = out;
                        try (var walk = Files.walk(root)) {
                            walk.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                                try {
                                    Path target = Path.of(outPath).resolve(root.relativize(f).toString());
                                    Files.createDirectories(target.getParent());
                                    Files.writeString(target, Files.readString(f));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                        Path outDir = Path.of(out).resolve("testpkg");
                        Files.createDirectories(outDir);
                        Files.writeString(outDir.resolve("MyClass_Dod.java"),
                                "package testpkg;\\npublic class MyClass_Dod { public static int foo() { return 42; } }\\n");
                    }
                }
                """, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler.run(null, null, null, "-d", outDir.toString(), src.toString()) != 0) {
            throw new IllegalStateException("stub tool compilation failed");
        }
        return outDir;
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
