public class DefenderB extends SoccerBot {

    public DefenderB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (ML team): zone defense pura (SBSP sin marcaje).
        //   Si el balón está en mi mitad y soy closest-to-ball del equipo, ir al balón.
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 4.5);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new DefenderB(pickTeam(args, "B")).run();
    }
}
