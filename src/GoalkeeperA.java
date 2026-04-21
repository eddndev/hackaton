public class GoalkeeperA extends SoccerBot {

    private static final double GOAL_LINE_OFFSET = 3.0;
    private static final int    LOOKAHEAD_TICKS  = 5;
    private static final double RUSH_DISTANCE    = 18.0;

    public GoalkeeperA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos   = s.myPos();
        Vec2 ballPos = s.ballPos();
        Vec2 goal    = ownGoal();

        if (canKick(s)) {
            return kickToward(ballPos, clearanceTarget(ballPos), 5.0);
        }

        if (ballPos.dist(goal) < RUSH_DISTANCE) {
            return moveToward(myPos, ballPos);
        }

        Vec2 predicted = predictor.predict(LOOKAHEAD_TICKS);
        double centerY  = FIELD_H / 2.0;
        double targetX  = goal.x + (attacksRight() ? GOAL_LINE_OFFSET : -GOAL_LINE_OFFSET);
        double targetY  = clamp(predicted.y,
                                centerY - GOAL_HALF_HEIGHT,
                                centerY + GOAL_HALF_HEIGHT);

        return moveToward(myPos, new Vec2(targetX, targetY));
    }

    private Vec2 clearanceTarget(Vec2 ballPos) {
        double forwardX = attacksRight() ? FIELD_W : 0;
        double sideY    = (ballPos.y < FIELD_H / 2.0) ? 0 : FIELD_H;
        return new Vec2(forwardX, sideY);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperA(pickTeam(args, "A")).run();
    }
}
