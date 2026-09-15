package examples;

public class Game {
    public static float myMethod(MyClass myClass) {
        myClass.add();
        var array = myClass.myArray;
        return array[0];
    }

    public static float distSq(Vec a, Vec b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return dx * dx + dy * dy;
    }
}
