package examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlattenRewriteTest {

    @Test
    void callSiteIsRewrittenAndArrayMutationPersists() {
        int[] arr = {1, 2, 3};

        float result = Game.myMethod(2.0f, arr);

        assertEquals(3.0f, result);
        assertArrayEquals(new int[]{3, 4, 5}, arr);
    }

    @Test
    void expandedSignatureIsPublicApi() {
        int[] arr = {1, 1};
        MyClass_Dod.MyClass_add(2.0f, arr);
        assertArrayEquals(new int[]{3, 3}, arr);
    }

    @Test
    void soaStructIsCallableThroughPool() {
        int id = VecPool.alloc();
        VecPool.x[id] = 3.0f;
        VecPool.y[id] = 4.0f;
        assertEquals(25.0f, VecPool.lenSq(id));
    }

    @Test
    void fieldReadsAreExpandedInCallers() {
        int a = VecPool.alloc();
        int b = VecPool.alloc();
        VecPool.x[a] = 0.0f;
        VecPool.y[a] = 0.0f;
        VecPool.x[b] = 3.0f;
        VecPool.y[b] = 4.0f;
        assertEquals(25.0f, Game.distSq(a, b));
        VecPool.x[b] = 4.0f;
        VecPool.y[b] = 5.0f;
        assertEquals(41.0f, Game.distSq(a, b));
    }

    @Test
    void structLocalsAndFieldWritesWorkForSoa() {
        TestMain.main(new String[0]);
    }
}
