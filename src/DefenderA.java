public class DefenderA extends SoccerBot {

    private static final double MARK_GAP        = 2.0;
    private static final double THREAT_DISTANCE = 65.0;
    private static final double BASE_FROM_GOAL  = 35.0;
    private static final double Y_MIRROR        = 0.7;
    private static final double SPEED_PER_TICK  = 2.0;
    private static final int    CHASE_LOOKAHEAD = 6;

    public DefenderA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos   = s.myPos();
        Vec2 ballPos = s.ballPos();
        Vec2 myGoal  = ownGoal();

        if (canKick(s)) {
            return kickToward(ballPos, shotAimPoint(s), 5.0);
        }

        if (s.amClosestTeammateToBall(s.you.id) && ballInMyHalf(ballPos)) {
            Vec2 target = interceptPoint(myPos, SPEED_PER_TICK, CHASE_LOOKAHEAD);
            return moveToward(myPos, target);
        }

        GameState.Player threat = mostThreateningOpponent(s, myGoal);
        if (threat != null) {
            return moveToward(myPos, computeMarkPosition(threat.pos(), myGoal));
        }

        double baseX = attacksRight() ? BASE_FROM_GOAL : FIELD_W - BASE_FROM_GOAL;
        double homeY = FIELD_CENTER.y + (ballPos.y - FIELD_CENTER.y) * Y_MIRROR;
        return moveToward(myPos, new Vec2(baseX, homeY));
    }

    private boolean ballInMyHalf(Vec2 b) {
        return attacksRight() ? b.x < FIELD_W / 2.0 : b.x > FIELD_W / 2.0;
    }

    private GameState.Player mostThreateningOpponent(GameState.State s, Vec2 myGoal) {
        GameState.Player best = null;
        double bestDist = THREAT_DISTANCE;
        for (GameState.Player p : s.opponents()) {
            double d = p.pos().dist(myGoal);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private Vec2 computeMarkPosition(Vec2 opponent, Vec2 myGoal) {
        double dist = opponent.dist(myGoal);
        if (dist < 1e-6) return opponent;
        Vec2 dir = opponent.sub(myGoal).scale(1.0 / dist);
        return myGoal.add(dir.scale(Math.max(0, dist - MARK_GAP)));
    }

    public static void main(String[] args) throws Exception {
        new DefenderA(pickTeam(args, "A")).run();
    }
}
