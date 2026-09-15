package examples;

import dev.team_argentum.ga_utils.api.Struct;

@Struct(backend = Struct.Backend.FLATTEN)
public class MyClass {
    public float myValue;
    public int[] myArray;

    public void add() {
        for (int i = 0; i < myArray.length; i++) {
            myArray[i] += (int) myValue;
        }
    }
}
