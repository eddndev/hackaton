public class DefenderA extends SoccerBot {

    public DefenderA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (Utility AI team): SBSP (attraction 0.2) + man-marking con histéresis
        //   - home = base_pos + (ball - FIELD_CENTER) * 0.2
        //   - si rival más cercano al balón en tu mitad: marcar
        //   - si soy closest-to-ball del equipo y no hay otro en ataque, ir al balón
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 4.5);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new DefenderA(pickTeam(args, "A")).run();
    }
}
