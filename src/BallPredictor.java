public class BallPredictor {
    private static final double DECAY = 0.94;

    private Vec2 prev = null;
    private Vec2 vel  = new Vec2(0, 0);

    public void update(Vec2 pos) {
        if (prev != null) vel = pos.sub(prev);
        prev = pos;
    }

    public Vec2 current() { return prev == null ? Vec2.zero() : prev; }
    public Vec2 velocity() { return vel; }

    public Vec2 predict(int ticksAhead) {
        if (prev == null) return Vec2.zero();
        double factor = (1.0 - Math.pow(DECAY, ticksAhead)) / (1.0 - DECAY);
        return prev.add(vel.scale(factor));
    }

    public int timeToReach(Vec2 from, double mySpeedPerTick, int maxTicks) {
        int best = -1;
        double bestSlack = Double.MAX_VALUE;
        for (int t = 1; t <= maxTicks; t++) {
            Vec2 b = predict(t);
            double reach = mySpeedPerTick * t;
            double gap = from.dist(b) - reach;
            if (gap <= 0 && Math.abs(gap) < bestSlack) {
                bestSlack = Math.abs(gap);
                best = t;
            }
        }
        return best;
    }
}
