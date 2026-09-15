package examples;

public class TestMain {

    public static void main(String[] args) {
        VecD d1 = new VecD();
        VecD d2 = new VecD();

        d1.x1 = new double[20];
        d1.y1 = new double[20];
        d2.x1 = new double[20];
        d2.y1 = new double[20];

        for (int i = 0; i < 20; i++) {
            d1.x1[i] = i;
            d1.y1[i] = i * 2;
            d2.x1[i] = i * 2;
            d2.y1[i] = i;
        }

        sum(d1, d2);

        System.out.println(d1.x1[1] + " " + d1.y1[0]);
        System.out.println(d2.x1[0] + " " + d2.y1[0]);
    }

    private static float test(Vec a, Vec b) {
        return a.x + b.x + a.y + b.y;
    }

    private static void sum(VecD a, VecD b) {
        for (int i = 0; i < a.x1.length; i++) {
            a.x1[i] += b.x1[i];
        }
        for (int i = 0; i < a.y1.length; i++) {
            a.y1[i] += b.y1[i];
        }
    }
}
