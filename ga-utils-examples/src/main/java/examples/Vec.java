package examples;

import dev.team_argentum.ga_utils.api.Struct;

@Struct(backend = Struct.Backend.SOA)
public class Vec {
    public float x;
    public float y;

    public float lenSq() {
        return x * x + y * y;
    }
}
