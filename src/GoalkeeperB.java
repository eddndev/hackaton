public class GoalkeeperB extends SoccerBot {

    public GoalkeeperB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (ML team): GK reactivo — pegado a ownGoal().x, Y sigue al balón (clamp al área chica).
        //   Opcional: si el balón entra al área, salir a intercept.
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperB(pickTeam(args, "B")).run();
    }
}
