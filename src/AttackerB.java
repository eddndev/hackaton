public class AttackerB extends SoccerBot {

    public AttackerB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (ML team): Bandit UCB1 sobre 3-4 meta-estrategias cada N ticks:
        //   - ATTACK   : ir al balón y patear a portería
        //   - SUPPORT  : abrirse, esperar pase
        //   - PRESS    : acosar al rival con balón
        //   - COUNTER  : quedarse adelantado para contragolpe
        //   Recompensa: +1 si hay gol a favor, -1 si en contra, 0 en otro caso.
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new AttackerB(pickTeam(args, "B")).run();
    }
}
