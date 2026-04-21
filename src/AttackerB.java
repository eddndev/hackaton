public class AttackerB extends SoccerBot {

    private int ticks = 0;
    private int currentStrategy = -1;
    private final double[] rewards = new double[4];
    private final int[] counts = new int[4];
    private int totalSelections = 0;
    private Vec2 lastBallPos = null;

    public AttackerB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 5.0);

        // 1. Calcular Recompensa Heurística (si avanzamos el balón hacia la portería rival)
        if (lastBallPos != null && currentStrategy != -1) {
            double distBefore = lastBallPos.dist(opponentGoal());
            double distNow = s.ballPos().dist(opponentGoal());
            rewards[currentStrategy] += (distBefore - distNow); 
        }
        lastBallPos = s.ballPos();

        // 2. Ejecutar Bandit UCB1 cada 30 ticks
        ticks++;
        if (ticks >= 30 || totalSelections == 0) {
            ticks = 0;
            currentStrategy = selectBanditArm();
            counts[currentStrategy]++;
            totalSelections++;
        }

        // 3. Ejecutar Meta-Estrategia Seleccionada
        double opX = opponentGoal().x;
        int dir = attacksRight() ? -1 : 1; // Para restar a Team A y sumar a Team B

        switch(currentStrategy) {
            case 0: // ATTACK: Ir directo al balón
                return moveToward(s.myPos(), s.ballPos());
            case 1: // SUPPORT: Posicionarse a 20 unidades de la portería rival esperando pase
                return moveToward(s.myPos(), new Vec2(opX + (20 * dir), s.ballPos().y));
            case 2: // PRESS: Acosar al rival más cercano al balón
                GameState.Player op = s.closestTo(s.ballPos(), s.opponents());
                return op != null ? moveToward(s.myPos(), op.pos()) : moveToward(s.myPos(), s.ballPos());
            case 3: // COUNTER: Quedarse adelantado al centro
                return moveToward(s.myPos(), new Vec2(opX + (10 * dir), FIELD_CENTER.y));
            default:
                return stay();
        }
    }

    private int selectBanditArm() {
        for (int i = 0; i < 4; i++) {
            if (counts[i] == 0) return i; // Explorar brazos no visitados
        }
        int best = 0;
        double bestScore = -Double.MAX_VALUE;
        double c = Math.sqrt(2); 
        
        // Ecuación UCB1: score_i = r_i + c * sqrt(ln(N_total) / n_i)
        for (int i = 0; i < 4; i++) {
            double avgReward = rewards[i] / counts[i];
            double score = avgReward + c * Math.sqrt(Math.log(totalSelections) / counts[i]);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public static void main(String[] args) throws Exception {
        new AttackerB(pickTeam(args, "B")).run();
    }
}
