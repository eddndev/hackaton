public class AttackerA extends SoccerBot {

    public AttackerA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // TODO (Utility AI team): scoring de 5 acciones
        //   1. SHOOT    (si dist a portería < umbral y línea libre)
        //   2. PASS     (si teammate mejor posicionado, pase sin rival en línea)
        //   3. DRIBBLE  (llevar balón hacia opponentGoal con el vector limpio)
        //   4. PRESS    (si no tengo balón y soy closest-to-ball del equipo)
        //   5. SUPPORT  (si teammate tiene balón, abrirse a zona de recepción)
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new AttackerA(pickTeam(args, "A")).run();
    }
}
