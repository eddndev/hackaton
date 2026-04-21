public class Vec2 {
    public final double x, y;

    public Vec2(double x, double y) { this.x = x; this.y = y; }

    public static Vec2 of(double[] a) { return new Vec2(a[0], a[1]); }
    public static Vec2 zero() { return new Vec2(0, 0); }

    public Vec2 add(Vec2 o)      { return new Vec2(x + o.x, y + o.y); }
    public Vec2 sub(Vec2 o)      { return new Vec2(x - o.x, y - o.y); }
    public Vec2 scale(double s)  { return new Vec2(x * s, y * s); }
    public double dot(Vec2 o)    { return x * o.x + y * o.y; }
    public double len()          { return Math.sqrt(x * x + y * y); }
    public double len2()         { return x * x + y * y; }
    public double dist(Vec2 o)   { return sub(o).len(); }

    public Vec2 norm() {
        double l = len();
        return l < 1e-9 ? new Vec2(0, 0) : new Vec2(x / l, y / l);
    }

    public Vec2 clamp(double minX, double maxX, double minY, double maxY) {
        return new Vec2(
            Math.max(minX, Math.min(maxX, x)),
            Math.max(minY, Math.min(maxY, y))
        );
    }

    @Override
    public String toString() { return String.format("(%.1f,%.1f)", x, y); }
}
