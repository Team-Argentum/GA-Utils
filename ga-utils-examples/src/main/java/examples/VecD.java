package examples;

import dev.team_argentum.ga_utils.api.Struct;

@Struct(backend = Struct.Backend.FLATTEN)
public class VecD {
    public double[] x1;
    public double[] y1;

    public VecD(double[] x1, double[] y1) {
        this.x1 = x1;
        this.y1 = y1;
    }

    public VecD() {}
}
