package dev.team_argentum.ga_utils.struct;

import dev.team_argentum.ga_utils.core.SourceFile;
import dev.team_argentum.ga_utils.core.TransformResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlattenBackendTest {

    private static final String SRC = """
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

            class Callers {
                public static void myMethod(MyClass myClass) {
                    myClass.add();
                    var array = myClass.myArray;
                    if (array.length > 0) {
                        myMethod(myClass);
                    }
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
    void generatesFlattenedMethods() {
        TransformResult r = run("flatten", SRC);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());

        var dod = r.output("test/MyClass_Dod.java").orElseThrow();
        assertTrue(dod.content().contains("public static void MyClass_add(float MyClass_myValue, int[] MyClass_myArray)"));
        assertTrue(dod.content().contains("MyClass_myArray[i] += (int) MyClass_myValue;"));
        assertTrue(dod.content().contains("MyClass_myArray.length"));
    }

    @Test
    void rewritesCallSites() {
        TransformResult r = run("flatten", SRC);
        assertFalse(r.hasErrors());

        var callers = r.output("Src0.java").orElseThrow();
        assertTrue(callers.content().contains("public static void myMethod(float myClass_myValue, int[] myClass_myArray)"));
        assertTrue(callers.content().contains("MyClass_Dod.MyClass_add(myClass_myValue, myClass_myArray);"));
        assertTrue(callers.content().contains("var array = myClass_myArray;"));
        assertTrue(callers.content().contains("myMethod(myClass_myValue, myClass_myArray);"));
        assertFalse(callers.content().contains("MyClass myClass"));
    }

    @Test
    void arrayOnlyStructLocalsExpandUnderFlatten() {
        TransformResult r = run("flatten", """
                package test;

                import dev.team_argentum.ga_utils.api.Struct;

                @Struct
                class Arrs {
                    public int[] a;
                    public int[] b;
                }

                class M {
                    static void run() {
                        Arrs x = new Arrs();
                        x.a = new int[2];
                        x.a[0] = 5;
                        use(x);
                    }

                    static int use(Arrs v) {
                        return v.a[0];
                    }
                }
                """);
        r.diagnostics().forEach(d -> System.out.println(d));
        assertFalse(r.hasErrors());

        var caller = r.output("Src0.java").orElseThrow();
        assertTrue(caller.content().contains("int[] x_a = null;"));
        assertTrue(caller.content().contains("int[] x_b = null;"));
        assertTrue(caller.content().contains("x_a[0] = 5;"));
        assertTrue(caller.content().contains("use(x_a, x_b);"));
        assertTrue(caller.content().contains("static int use(int[] v_a, int[] v_b)"));
    }

    @Test
    void rejectsScalarMutation() {
        String src = """
                package test;

                @Struct
                class Counter {
                    public float value;
                    public void increment() {
                        value++;
                    }
                }
                """;
        TransformResult r = run("flatten", src);
        assertTrue(r.hasErrors());
        assertTrue(r.outputs().isEmpty());
    }
}
