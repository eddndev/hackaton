public class GoalkeeperA extends SoccerBot {

    private static final double MAX_ADVANCE      = 28.0;
    private static final double ADVANCE_SLOPE    = 4.0;
    private static final double ADVANCE_MIN_DIST = 30.0;
    private static final double RUSH_DISTANCE    = 18.0;
    private static final int    LOOKAHEAD_BASE   = 4;
    private static final double Y_EXPANSION_GAIN = 0.8;

    public GoalkeeperA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos = s.myPos();
        Vec2 ball  = s.ballPos();
        Vec2 goal  = ownGoal();

        if (canKick(s)) {
            return kickToward(ball, clearanceTarget(ball), 5.0);
        }

        double ballDist = ball.dist(goal);
        if (ballDist < RUSH_DISTANCE) {
            return moveToward(myPos, ball);
        }

        double advance  = clamp((ballDist - ADVANCE_MIN_DIST) / ADVANCE_SLOPE, 0, MAX_ADVANCE);
        int    lookahead = LOOKAHEAD_BASE + (int) (advance / 8.0);
        double yRange   = GOAL_HALF_HEIGHT + advance * Y_EXPANSION_GAIN;

        Vec2   predicted = predictor.predict(lookahead);
        double targetX   = goal.x + (attacksRight() ? advance : -advance);
        double targetY   = clamp(predicted.y, FIELD_H / 2.0 - yRange, FIELD_H / 2.0 + yRange);

        return moveToward(myPos, new Vec2(targetX, targetY));
    }

    private Vec2 clearanceTarget(Vec2 ball) {
        double forwardX = attacksRight() ? FIELD_W : 0;
        double sideY    = ball.y < FIELD_H / 2.0 ? 0 : FIELD_H;
        return new Vec2(forwardX, sideY);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperA(pickTeam(args, "A")).run();
    }
}
