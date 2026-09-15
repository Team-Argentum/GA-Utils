package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendAnnotationTest {

    private static TransformResult run(String backend, String... sources) {
        List<SourceFile> in = new java.util.ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            in.add(new SourceFile("Src" + i + ".java", sources[i]));
        }
        Map<String, String> options = backend == null ? Map.of() : Map.of("backend", backend);
        return new StructExpandProcessor().process(in, options);
    }

    @Test
    void annotationBackendOverridesGlobalOption() {
        TransformResult r = run("flatten", """
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
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());
        assertTrue(r.output("test/UnitPool.java").isPresent());
        assertFalse(r.outputs().stream().anyMatch(f -> f.path().contains("_Dod")));
    }

    @Test
    void structsCanUseDifferentBackendsSimultaneously() {
        TransformResult r = run("flatten", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct(backend = Struct.Backend.SOA)
                class Unit {
                    public float health;

                    public void takeDamage(float amount) {
                        health -= amount;
                    }
                }

                @Struct(backend = Struct.Backend.FLATTEN)
                class Vec {
                    public float x;
                    public float y;

                    public float lenSq() {
                        return x * x + y * y;
                    }
                }
                """);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());
        assertTrue(r.output("test/UnitPool.java").isPresent());
        assertTrue(r.output("test/Vec_Dod.java").isPresent());
    }

    @Test
    void defaultBackendIsUsedWhenAnnotationUnset() {
        TransformResult r = run(null, """
                package test;

                @Struct
                class Vec {
                    public float x;
                    public float y;

                    public float lenSq() {
                        return x * x + y * y;
                    }
                }
                """);
        assertFalse(r.hasErrors());
        assertTrue(r.output("test/Vec_Dod.java").isPresent());
    }

    @Test
    void unknownBackendIsReported() {
        TransformResult r = run("flatten", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct(backend = Struct.Backend.UNSET)
                class Vec {
                    public float x;
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.outputs().isEmpty());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("unknown backend 'unset'")));
    }
}
