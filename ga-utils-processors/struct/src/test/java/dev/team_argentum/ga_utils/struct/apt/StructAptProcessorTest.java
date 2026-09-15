package dev.team_argentum.ga_utils.struct.apt;

import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.readString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructAptProcessorTest {

    record CompileResult(boolean ok, List<javax.tools.Diagnostic<? extends JavaFileObject>> diagnostics,
                        Path genDir, Path classesDir) {
        String messages() {
            StringBuilder sb = new StringBuilder();
            for (var d : diagnostics) {
                sb.append(d).append('\n');
            }
            return sb.toString();
        }
    }

    private CompileResult compile(String backend, List<Path> files, String extraClasspath) throws IOException {
        Path classesDir = Files.createTempDirectory("ga-classes");
        Path genDir = Files.createTempDirectory("ga-gen");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(collector, null, StandardCharsets.UTF_8);
        String cp = System.getProperty("gautils.test.classpath", System.getProperty("java.class.path"));
        if (extraClasspath != null && !extraClasspath.isEmpty()) {
            cp = cp + java.io.File.pathSeparator + extraClasspath;
        }
        List<String> options = List.of(
                "-d", classesDir.toString(),
                "-s", genDir.toString(),
                "-processor", StructAptProcessor.class.getName(),
                "-cp", cp,
                "-Agautils.backend=" + backend);
        StringWriter out = new StringWriter();
        JavaCompiler.CompilationTask task = compiler.getTask(out, fm, collector, options, null,
                fm.getJavaFileObjects(files.toArray(new Path[0])));
        boolean ok = task.call();
        fm.close();
        return new CompileResult(ok, collector.getDiagnostics(), genDir, classesDir);
    }

    private static Path writeSource(String relativePath, String source) throws IOException {
        Path srcDir = Files.createTempDirectory("ga-src");
        Path file = srcDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void generatesCompilingPoolForSoa() throws IOException {
        Path src = writeSource("test/Unit.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class Unit {
                    public float health;
                    public float speed;

                    public void takeDamage(float amount) {
                        health -= amount;
                    }
                }
                """);

        CompileResult r = compile("soa", List.of(src), null);
        assertTrue(r.ok, r.messages());

        Path pool = r.genDir().resolve("test/UnitPool.java");
        assertTrue(Files.exists(pool), r.messages());
        String content = readString(pool);
        assertTrue(content.contains("public static float[] health = new float[CAPACITY];"));
        assertTrue(content.contains("health[__ga_id] -= amount;"));
    }

    @Test
    void generatedPoolIsUsableFromRealCode() throws IOException {
        Path src = writeSource("test/Unit.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class Unit {
                    public float health;

                    public void takeDamage(float amount) {
                        health -= amount;
                    }
                }
                """);

        CompileResult first = compile("soa", List.of(src), null);
        assertTrue(first.ok, first.messages());

        Path caller = writeSource("test/Game.java", """
                package test;

                class Game {
                    public static void update(int unitId, float amount) {
                        UnitPool.takeDamage(unitId, amount);
                    }
                }
                """);

        CompileResult second = compile("soa", List.of(caller),
                first.genDir().toString() + java.io.File.pathSeparator + first.classesDir().toString());
        assertTrue(second.ok, second.messages());
    }

    @Test
    void flattenGeneratesCompilingDodClass() throws IOException {
        Path src = writeSource("test/MyClass.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class MyClass {
                    public float myValue;
                    public int[] myArray;

                    public void add() {
                        for (int i = 0; i < myArray.length; i++) {
                            myArray[i] += (int) myValue;
                        }
                    }
                }
                """);

        CompileResult r = compile("flatten", List.of(src), null);
        assertTrue(r.ok, r.messages());

        Path dod = r.genDir().resolve("test/MyClass_Dod.java");
        assertTrue(Files.exists(dod), r.messages());
        String content = readString(dod);
        assertTrue(content.contains("public static void MyClass_add(float MyClass_myValue, int[] MyClass_myArray)"));
    }

    @Test
    void annotationOverridesGlobalBackendOption() throws IOException {
        Path src = writeSource("test/Unit.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct(backend = Struct.Backend.SOA)
                class Unit {
                    public float health;

                    public void takeDamage(float amount) {
                        health -= amount;
                    }
                }
                """);

        CompileResult r = compile("flatten", List.of(src), null);
        assertTrue(r.ok, r.messages());
        assertTrue(Files.exists(r.genDir().resolve("test/UnitPool.java")), r.messages());
        assertFalse(Files.exists(r.genDir().resolve("test/Unit_Dod.java")), r.messages());
    }

    @Test
    void ssaGeneratesCompilingMutatingMethods() throws IOException {
        Path src = writeSource("test/Counter.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class Counter {
                    public float value;

                    public void increment() {
                        value++;
                    }
                }
                """);

        CompileResult r = compile("ssa", List.of(src), null);
        assertTrue(r.ok, r.messages());

        String content = readString(r.genDir().resolve("test/Counter_Dod.java"));
        assertTrue(content.contains("public static float Counter_increment(float Counter_value)"), content);
        assertTrue(content.contains("return Counter_value;"), content);
    }

    @Test
    void flattenRejectsScalarMutationAtCompileTime() throws IOException {
        Path src = writeSource("test/Counter.java", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class Counter {
                    public float value;

                    public void increment() {
                        value++;
                    }
                }
                """);

        CompileResult r = compile("flatten", List.of(src), null);
        assertFalse(r.ok, r.messages());
        assertTrue(r.messages().contains("cannot persist mutation of scalar field"), r.messages());
    }
}
