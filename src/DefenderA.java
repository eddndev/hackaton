public class DefenderA extends SoccerBot {

    private static final double MARK_GAP         = 2.0;
    private static final double BASE_FROM_GOAL   = 35.0;
    private static final double SPEED_PER_TICK   = 2.0;
    private static final int    CHASE_LOOKAHEAD  = 6;
    private static final double REPULSION_DIST   = 10.0;
    private static final double REPULSION_GAIN   = 0.3;
    private static final int    CHASE_LOCK_TICKS = 6;
    private static final double PASS_RADIUS      = 3.0;
    private static final double PASS_SCORE_MIN   = 0.2;

    private final TacticsA.BotMemory mem = new TacticsA.BotMemory();
    private int chaseLock = 0;

    public DefenderA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos  = s.myPos();
        Vec2 ball   = s.ballPos();
        Vec2 myGoal = ownGoal();

        mem.update(myPos);
        if (chaseLock > 0) chaseLock--;

        if (canKick(s)) {
            return kickActionFromDef(s);
        }

        int rank = teamRank(s);
        TacticsA.Phase phase = TacticsA.detectPhase(s, attacksRight(), FIELD_W);

        boolean ballReachable = attacksRight()
                ? ball.x < FIELD_W * 0.72
                : ball.x > FIELD_W * 0.28;

        boolean ballInDefThird = attacksRight()
                ? ball.x < FIELD_W * 0.33
                : ball.x > FIELD_W * 0.67;
        GameState.Player nearRival = s.closestTo(ball, s.opponents());
        boolean rivalControls = nearRival != null && nearRival.pos().dist(ball) < 8.0;

        boolean emergency = ballInDefThird && rivalControls;

        boolean shouldChase = (rank == 0 && ballReachable) || emergency;
        if (shouldChase) {
            chaseLock = CHASE_LOCK_TICKS;
        } else if (chaseLock > 0 && ballReachable) {
            shouldChase = true;
        }

        Vec2 target;
        if (shouldChase) {
            target = interceptPoint(myPos, SPEED_PER_TICK, CHASE_LOOKAHEAD);
        } else if (phase == TacticsA.Phase.DEFENSE) {
            GameState.Player threat = TacticsA.highestThreat(s, myGoal);
            target = threat != null
                    ? computeMarkPosition(threat.pos(), myGoal)
                    : holdingPosition(ball);
        } else if (!ballInMyHalf(ball)) {
            target = supportSlot(s, rank);
        } else {
            target = holdingPosition(ball);
        }

        Vec2 push = repulsionFromTeammates(s, REPULSION_DIST);
        return moveToward(myPos, target.add(push.scale(REPULSION_GAIN)));
    }

    private String kickActionFromDef(GameState.State s) {
        GameState.Player target = bestPassTarget(s, PASS_SCORE_MIN);
        if (target != null) {
            Vec2 leadTo = leadPosition(target, 3);
            double dist = s.ballPos().dist(leadTo);
            return kickToward(s.ballPos(), leadTo, kickPowerForDistance(dist));
        }
        GameState.Player back = safestBackwardTeammate(s);
        if (back != null && back.pos().dist(ownGoal()) > 25) {
            Vec2 leadTo = leadPosition(back, 3);
            double dist = s.ballPos().dist(leadTo);
            return kickToward(s.ballPos(), leadTo, kickPowerForDistance(dist));
        }
        return kickToward(s.ballPos(), shotAimPoint(s), 5.0);
    }

    private Vec2 holdingPosition(Vec2 ball) {
        double ownGoalX = attacksRight() ? 0 : FIELD_W;
        double holdX    = ownGoalX + (ball.x - ownGoalX) * 0.5;
        if (attacksRight()) {
            holdX = Math.max(BASE_FROM_GOAL - 10, Math.min(FIELD_W * 0.45, holdX));
        } else {
            holdX = Math.max(FIELD_W * 0.55, Math.min(FIELD_W - BASE_FROM_GOAL + 10, holdX));
        }
        double holdY = FIELD_CENTER.y + (ball.y - FIELD_CENTER.y) * 0.7;
        return new Vec2(holdX, holdY);
    }

    private boolean ballInMyHalf(Vec2 b) {
        return attacksRight() ? b.x < FIELD_W / 2.0 : b.x > FIELD_W / 2.0;
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
