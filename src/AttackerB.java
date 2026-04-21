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
        if (canKick(s)) {
            // Encontrar al rival más cercano a su propia portería (seguro es el portero)
            GameState.Player enemyGoalie = s.closestTo(opponentGoal(), s.opponents());
            
            // Si el portero está en la mitad de arriba, apuntar abajo (y viceversa)
            double aimY = (enemyGoalie != null && enemyGoalie.y < FIELD_CENTER.y) 
                        ? FIELD_CENTER.y + GOAL_HALF_HEIGHT - 2.0  // Esquina inferior
                        : FIELD_CENTER.y - GOAL_HALF_HEIGHT + 2.0; // Esquina superior
                        
            return kickToward(s.ballPos(), new Vec2(opponentGoal().x, aimY), 5.0);
        }
        // --- 1. OVERRIDE DE SUPERVIVENCIA (El Sentido Común) ---
        // Si soy el jugador de mi equipo más cercano al balón, ABANDONO EL BANDIT Y ATACÓ.
        if (s.amClosestTeammateToBall(this.playerId)) {
            // Usar el predictor para ir a donde ESTARÁ el balón en 4 ticks (400ms en el futuro).
            Vec2 futureBall = predictor.predict(4); 
            return moveToward(s.myPos(), futureBall);
        }

        // --- 2. EVALUACIÓN DE RECOMPENSA HEURÍSTICA ---
        if (lastBallPos != null && currentStrategy != -1) {
            double distBefore = lastBallPos.dist(opponentGoal());
            double distNow = s.ballPos().dist(opponentGoal());
            rewards[currentStrategy] += (distBefore - distNow); 
        }
        lastBallPos = s.ballPos();

        // --- 3. BANDIT UCB1 (Solo opera si NO soy el más cercano al balón) ---
        ticks++;
        if (ticks >= 30 || totalSelections == 0) {
            ticks = 0;
            currentStrategy = selectBanditArm();
            counts[currentStrategy]++;
            totalSelections++;
        }

        double opX = opponentGoal().x;
        int dir = attacksRight() ? -1 : 1; 

        // --- 4. MOVIMIENTO SIN BALÓN ---
        switch(currentStrategy) {
            case 0: // ATTACK
                return moveToward(s.myPos(), s.ballPos());
            case 1: // SUPPORT
                return moveToward(s.myPos(), new Vec2(opX + (20 * dir), s.ballPos().y));
            case 2: // PRESS
                GameState.Player op = s.closestTo(s.ballPos(), s.opponents());
                return op != null ? moveToward(s.myPos(), op.pos()) : moveToward(s.myPos(), s.ballPos());
            case 3: // COUNTER
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
