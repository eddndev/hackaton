public class GoalkeeperA extends SoccerBot {

    private static final double MAX_ADVANCE       = 40.0;
    private static final double RUSH_DISTANCE     = 16.0;
    private static final double DANGER_RADIUS     = 50.0;
    private static final double RIVAL_CONTROL_GAP = 10.0;
    private static final double Y_EXPANSION_GAIN  = 0.7;
    private static final double APPROACH_SPEED    = 0.5;
    private static final double APPROACH_BONUS    = 5.0;
    private static final double BALL_CLEARANCE    = 4.0;
    private static final int    LOOKAHEAD_BASE    = 4;

    public GoalkeeperA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos = s.myPos();
        Vec2 ball  = s.ballPos();
        Vec2 goal  = ownGoal();

        if (canKick(s)) {
            return kickActionFromGK(s);
        }

        double ballDistGoal = ball.dist(goal);
        if (ballDistGoal < RUSH_DISTANCE) {
            return moveToward(myPos, ball);
        }

        GameState.Player nearestRival = s.closestTo(ball, s.opponents());
        double rivalBallDist = nearestRival != null
                ? nearestRival.pos().dist(ball)
                : Double.MAX_VALUE;
        boolean rivalControls = rivalBallDist < RIVAL_CONTROL_GAP;
        boolean dangerZone    = ballDistGoal < DANGER_RADIUS;

        boolean approaching = false;
        if (nearestRival != null) {
            Vec2 rivalVel = playerVelocity(nearestRival.id);
            if (rivalVel.len() > 0.3) {
                Vec2 toGoalUnit = goal.sub(nearestRival.pos());
                double toGoalLen = toGoalUnit.len();
                if (toGoalLen > 1e-6) {
                    double dot = rivalVel.dot(toGoalUnit.scale(1.0 / toGoalLen));
                    approaching = dot > APPROACH_SPEED;
                }
            }
        }

        double advance;
        if (!rivalControls) {
            advance = Math.min(MAX_ADVANCE, ballDistGoal * 0.28);
        } else if (dangerZone) {
            advance = Math.min(MAX_ADVANCE, ballDistGoal * 0.45);
        } else {
            advance = Math.min(MAX_ADVANCE * 0.85, ballDistGoal * 0.22);
        }
        if (approaching) advance += APPROACH_BONUS;
        advance = Math.max(3.0, Math.min(advance, ballDistGoal - BALL_CLEARANCE));

        Vec2 dirGoalToBall = ball.sub(goal);
        double dirLen = dirGoalToBall.len();
        if (dirLen < 1e-6) return moveToward(myPos, ball);
        Vec2 unitDir = dirGoalToBall.scale(1.0 / dirLen);
        Vec2 arcTarget = goal.add(unitDir.scale(advance));

        int lookahead = LOOKAHEAD_BASE + (int) (advance / 8.0) + (approaching ? 2 : 0);
        Vec2 predicted = predictor.predict(lookahead);
        double yRange = GOAL_HALF_HEIGHT + advance * Y_EXPANSION_GAIN;
        double targetY = clamp(predicted.y,
                FIELD_CENTER.y - yRange,
                FIELD_CENTER.y + yRange);

        return moveToward(myPos, new Vec2(arcTarget.x, targetY));
    }

    private String kickActionFromGK(GameState.State s) {
        GameState.Player target = bestPassTarget(s, 0.25);
        if (target != null) {
            Vec2 leadTo = leadPosition(target, 3);
            double dist = s.ballPos().dist(leadTo);
            return kickToward(s.ballPos(), leadTo, kickPowerForDistance(dist));
        }
        return kickToward(s.ballPos(), clearanceTarget(s.ballPos()), 5.0);
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
