public class GoalkeeperA extends SoccerBot {

    public GoalkeeperA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (Utility AI team): intercept predictor + clamp a línea de portería.
        //   - línea X: ownGoal().x ± ~3
        //   - Y:       predictor.predict(3..8 ticks).y, clamp a [center-GOAL_HALF_HEIGHT, center+GOAL_HALF_HEIGHT]
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperA(pickTeam(args, "A")).run();
    }
}
