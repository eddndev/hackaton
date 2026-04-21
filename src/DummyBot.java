public class DummyBot extends SoccerBot {

    public DummyBot(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new DummyBot(pickTeam(args, "A")).run();
    }
}
