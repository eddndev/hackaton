public class DefenderA extends SoccerBot {

    private static final double MARK_GAP        = 2.0;
    private static final double BASE_FROM_GOAL  = 35.0;
    private static final double SPEED_PER_TICK  = 2.0;
    private static final int    CHASE_LOOKAHEAD = 6;
    private static final double REPULSION_DIST  = 10.0;
    private static final double REPULSION_GAIN  = 0.3;

    private final TacticsA.BotMemory mem = new TacticsA.BotMemory();

    public DefenderA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos  = s.myPos();
        Vec2 ball   = s.ballPos();
        Vec2 myGoal = ownGoal();

        mem.update(myPos);

        if (canKick(s)) {
            return kickToward(ball, shotAimPoint(s), 5.0);
        }

        int rank = teamRank(s);
        TacticsA.Phase phase = TacticsA.detectPhase(s, attacksRight(), FIELD_W);

        Vec2 target;

        boolean ballReachable = attacksRight()
                ? ball.x < FIELD_W * 0.72
                : ball.x > FIELD_W * 0.28;

        if (rank == 0 && ballReachable) {
            target = interceptPoint(myPos, SPEED_PER_TICK, CHASE_LOOKAHEAD);
        } else if (phase == TacticsA.Phase.DEFENSE) {
            GameState.Player threat = TacticsA.highestThreat(s, myGoal);
            target = threat != null
                    ? computeMarkPosition(threat.pos(), myGoal)
                    : homePosition(ball);
        } else if (phase == TacticsA.Phase.OFFENSE && rank >= 1) {
            target = supportSlot(s, rank);
        } else {
            target = homePosition(ball);
        }

        Vec2 push = repulsionFromTeammates(s, REPULSION_DIST);
        return moveToward(myPos, target.add(push.scale(REPULSION_GAIN)));
    }

    private Vec2 homePosition(Vec2 ball) {
        double baseX = attacksRight() ? BASE_FROM_GOAL : FIELD_W - BASE_FROM_GOAL;
        double homeY = FIELD_CENTER.y + (ball.y - FIELD_CENTER.y) * 0.7;
        return new Vec2(baseX, homeY);
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
