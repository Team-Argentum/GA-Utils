package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsaBackendTest {

    private static TransformResult run(String backend, String... sources) {
        List<SourceFile> in = new java.util.ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            in.add(new SourceFile("Src" + i + ".java", sources[i]));
        }
        return new StructExpandProcessor().process(in, Map.of("backend", backend));
    }

    private static final String COUNTER_SRC = """
            package test;

            import dev.team_argentum.ga_utils.api.Struct;

            @Struct(backend = Struct.Backend.SSA)
            class Counter {
                public float value;

                public void increment() {
                    value++;
                }

                public void add(float delta) {
                    value += delta;
                }
            }

            class Run {
                public static void process(Counter c) {
                    c.increment();
                    c.add(5.0f);
                    c.value = 10.0f;
                    float v = c.value;
                }
            }
            """;

    @Test
    void mutatingMethodsReturnUpdatedField() {
        TransformResult r = run("ssa", COUNTER_SRC);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());

        var dod = r.output("test/Counter_Dod.java").orElseThrow();
        assertTrue(dod.content().contains("public static float Counter_increment(float Counter_value)"));
        assertTrue(dod.content().contains("Counter_value++;"));
        assertTrue(dod.content().contains("return Counter_value;"));
        assertTrue(dod.content().contains("public static float Counter_add(float Counter_value, float delta)"));
        assertTrue(dod.content().contains("Counter_value += delta;"));
    }

    @Test
    void callSitesReassignUpdatedField() {
        TransformResult r = run("ssa", COUNTER_SRC);
        assertFalse(r.hasErrors());

        var caller = r.output("Src0.java").orElseThrow();
        assertTrue(caller.content().contains("public static void process(float c_value)"));
        assertTrue(caller.content().contains("c_value = Counter_Dod.Counter_increment(c_value);"));
        assertTrue(caller.content().contains("c_value = Counter_Dod.Counter_add(c_value, 5.0f);"));
        assertTrue(caller.content().contains("c_value = 10.0f;"));
        assertTrue(caller.content().contains("float v = c_value;"));
    }

    @Test
    void mutatingCallsInsideStructBodyReassignField() {
        TransformResult r = run("ssa", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct(backend = Struct.Backend.SSA)
                class Timer {
                    public float t;

                    public void tick() {
                        t += 1;
                    }

                    public void tickTwice() {
                        tick();
                        tick();
                    }
                }
                """);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());

        var dod = r.output("test/Timer_Dod.java").orElseThrow();
        assertTrue(dod.content().contains("Timer_t = Timer_Dod.Timer_tick(Timer_t);"));
        assertTrue(dod.content().contains("return Timer_t;"));
    }

    @Test
    void multiFieldMutationIsRejected() {
        TransformResult r = run("ssa", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct(backend = Struct.Backend.SSA)
                class Pair {
                    public float a;
                    public float b;

                    public void bumpBoth() {
                        a++;
                        b++;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.outputs().isEmpty());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains("mutates 2 scalar fields")));
    }
}
