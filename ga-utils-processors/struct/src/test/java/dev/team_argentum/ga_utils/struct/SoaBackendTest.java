package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoaBackendTest {

    private static final String SRC = """
            package test;

            import dev.team_argentum.ga_utils.api.Struct;

            @Struct
            class Unit {
                public float health;
                public float speed;

                public void takeDamage(float amount) {
                    health -= amount;
                }

                public boolean isDead() {
                    return health <= 0;
                }
            }

            class Game {
                public static void updateUnit(Unit unit, float damage) {
                    unit.takeDamage(damage);
                    if (unit.isDead()) {
                        unit.speed = 0.0f;
                    }
                }

                public static void spawn() {
                    Unit u = new Unit();
                    u.health = 100.0f;
                    updateUnit(u, 5.0f);
                }
            }
            """;

    private static TransformResult run(String backend, String... sources) {
        List<SourceFile> in = new java.util.ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            in.add(new SourceFile("Src" + i + ".java", sources[i]));
        }
        return new StructExpandProcessor().process(in, Map.of("backend", backend));
    }

    @Test
    void generatesPool() {
        TransformResult r = run("soa", SRC);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());

        var pool = r.output("test/UnitPool.java").orElseThrow();
        assertTrue(pool.content().contains("public static float[] health = new float[CAPACITY];"));
        assertTrue(pool.content().contains("public static float[] speed = new float[CAPACITY];"));
        assertTrue(pool.content().contains("public static int alloc()"));
        assertTrue(pool.content().contains("public static void takeDamage(int __ga_id, float amount)"));
        assertTrue(pool.content().contains("health[__ga_id] -= amount;"));
        assertTrue(pool.content().contains("return health[__ga_id] <= 0;"));
    }

    @Test
    void rewritesCallSites() {
        TransformResult r = run("soa", SRC);
        assertFalse(r.hasErrors());

        var game = r.output("Src0.java").orElseThrow();
        assertTrue(game.content().contains("public static void updateUnit(int unitId, float damage)"));
        assertTrue(game.content().contains("UnitPool.takeDamage(unitId, damage);"));
        assertTrue(game.content().contains("UnitPool.isDead(unitId)"));
        assertTrue(game.content().contains("UnitPool.speed[unitId] = 0.0f;"));
        assertTrue(game.content().contains("int uId = UnitPool.alloc();"));
        assertTrue(game.content().contains("UnitPool.health[uId] = 100.0f;"));
        assertTrue(game.content().contains("updateUnit(uId, 5.0f);"));
        assertFalse(game.content().contains("Unit unit"));
    }
}
