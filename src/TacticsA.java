public class TacticsA {

    public enum Phase { DEFENSE, TRANSITION, OFFENSE }

    public static Phase detectPhase(GameState.State s, boolean attacksRight, double fieldW) {
        double ballX = s.ballPos().x;
        boolean ballInMyHalf = attacksRight ? ballX < fieldW / 2.0 : ballX > fieldW / 2.0;

        double nearestMy = s.myPos().dist(s.ballPos());
        for (GameState.Player t : s.teammates(s.you.id)) {
            nearestMy = Math.min(nearestMy, t.pos().dist(s.ballPos()));
        }
        double nearestOpp = Double.MAX_VALUE;
        for (GameState.Player o : s.opponents()) {
            nearestOpp = Math.min(nearestOpp, o.pos().dist(s.ballPos()));
        }
        boolean weControl = nearestMy < nearestOpp;

        if (ballInMyHalf && !weControl) return Phase.DEFENSE;
        if (!ballInMyHalf && weControl) return Phase.OFFENSE;
        return Phase.TRANSITION;
    }

    public static double threatScore(GameState.Player opp, GameState.State s, Vec2 ownGoal) {
        double distGoal = opp.pos().dist(ownGoal);
        double distBall = opp.pos().dist(s.ballPos());
        double proxGoal = Math.max(0, 1.0 - distGoal / 100.0);
        double proxBall = Math.max(0, 1.0 - distBall / 80.0);
        Vec2 ballToGoal = ownGoal.sub(s.ballPos());
        Vec2 ballToOpp  = opp.pos().sub(s.ballPos());
        double bgLen = ballToGoal.len();
        double boLen = ballToOpp.len();
        double dirBonus = (bgLen < 1e-6 || boLen < 1e-6)
                ? 0
                : Math.max(0, ballToGoal.dot(ballToOpp) / (bgLen * boLen));
        return 0.5 * proxGoal + 0.3 * proxBall + 0.2 * dirBonus;
    }

    public static GameState.Player highestThreat(GameState.State s, Vec2 ownGoal) {
        GameState.Player best = null;
        double bestScore = -1;
        for (GameState.Player o : s.opponents()) {
            double score = threatScore(o, s, ownGoal);
            if (score > bestScore) { bestScore = score; best = o; }
        }
        return best;
    }

    public static Vec2 overlapPosition(GameState.State s, boolean attacksRight, double fieldW, double fieldH) {
        Vec2 ball = s.ballPos();
        double forward  = attacksRight ? 25 : -25;
        double lateralY = ball.y < fieldH / 2.0 ? 18 : -18;
        return new Vec2(
                clamp(ball.x + forward,  10, fieldW - 10),
                clamp(ball.y + lateralY,  8, fieldH - 8)
        );
    }

    public static Vec2 findOpenSpace(GameState.State s, boolean attacksRight, double fieldW, double fieldH) {
        Vec2 best = new Vec2(fieldW / 2.0, fieldH / 2.0);
        double bestMinDist = 0;
        double xMin = attacksRight ? fieldW / 2.0 + 5 : 10;
        double xMax = attacksRight ? fieldW - 10     : fieldW / 2.0 - 5;
        for (double x = xMin; x <= xMax; x += 12) {
            for (double y = 12; y <= fieldH - 12; y += 12) {
                Vec2 c = new Vec2(x, y);
                double minDist = Double.MAX_VALUE;
                for (GameState.Player o : s.opponents()) {
                    minDist = Math.min(minDist, c.dist(o.pos()));
                }
                if (minDist > bestMinDist) {
                    bestMinDist = minDist;
                    best = c;
                }
            }
        }
        return best;
    }

    public static Vec2 passLaneCut(GameState.State s, Vec2 ownGoal) {
        GameState.Player carrier = s.closestTo(s.ballPos(), s.opponents());
        if (carrier == null) return s.ballPos();
        Vec2 bestCut = s.ballPos();
        double bestAlign = -1;
        Vec2 ballToGoal = ownGoal.sub(s.ballPos());
        double bgLen = ballToGoal.len();
        if (bgLen < 1e-6) return s.ballPos();
        Vec2 bgU = ballToGoal.scale(1.0 / bgLen);
        for (GameState.Player o : s.opponents()) {
            if (o.id == carrier.id) continue;
            Vec2 ballToOpp = o.pos().sub(s.ballPos());
            double len = ballToOpp.len();
            if (len < 1e-6) continue;
            double align = ballToOpp.scale(1.0 / len).dot(bgU);
            if (align > bestAlign) {
                bestAlign = align;
                bestCut = s.ballPos().add(ballToOpp.scale(0.5));
            }
        }
        return bestCut;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static class BotMemory {
        public int   stuckTicks   = 0;
        public Vec2  lastPos      = null;
        public String lastRole    = "";
        public int   roleLockedFor = 0;

        public void update(Vec2 currentPos) {
            if (lastPos != null && currentPos.dist(lastPos) < 0.5) stuckTicks++;
            else stuckTicks = 0;
            lastPos = currentPos;
            if (roleLockedFor > 0) roleLockedFor--;
        }

        public boolean isStuck(int threshold) { return stuckTicks > threshold; }
        public void resetStuck() { stuckTicks = 0; }

        public void lockRole(String role, int ticks) {
            lastRole = role;
            roleLockedFor = ticks;
        }

        public boolean roleLocked() { return roleLockedFor > 0; }
    }
}
