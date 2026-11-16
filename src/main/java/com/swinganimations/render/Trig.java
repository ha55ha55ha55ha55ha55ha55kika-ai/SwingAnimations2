package com.swinganimations.render;

/** Готовые таблицы sin/cos для окружностей: в кадре никаких Math.sin/cos на каждую вершину. */
public final class Trig {
    private static final double[][] COS = new double[129][];
    private static final double[][] SIN = new double[129][];

    private Trig() {}

    private static void build(int n) {
        double[] c = new double[n + 1];
        double[] s = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            c[i] = Math.cos(a);
            s[i] = Math.sin(a);
        }
        COS[n] = c;
        SIN[n] = s;
    }

    /** n + 1 значений (замкнутая окружность), n от 3 до 128. */
    public static double[] cos(int n) {
        if (COS[n] == null) build(n);
        return COS[n];
    }

    public static double[] sin(int n) {
        if (SIN[n] == null) build(n);
        return SIN[n];
    }
}
