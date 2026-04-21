public class DefenderB extends SoccerBot {

    public DefenderB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        if (canKick(s)) return kickToward(s.ballPos(), opponentGoal(), 4.5);

        Vec2 ball = s.ballPos();
        
        // Determinar si el balón está en mi mitad del campo (Team B defiende la derecha, x > FIELD_CENTER.x)
        boolean ballInMyHalf = attacksRight() ? ball.x < FIELD_CENTER.x : ball.x > FIELD_CENTER.x;
        
        // Si soy el compañero más cercano y el balón está en mi zona, atacar el balón
        if (ballInMyHalf && s.amClosestTeammateToBall(this.playerId)) {
            return moveToward(s.myPos(), ball);
        }

        // Posición base para el equipo B (145 en X)
        Vec2 base = attacksRight() ? new Vec2(30, ownGoal().y) : new Vec2(145, ownGoal().y);
        
        // Calcular "Home" atrayendo la base hacia el balón multiplicando el vector por 0.25
        Vec2 home = base.add(ball.sub(FIELD_CENTER).scale(0.25));

        return moveToward(s.myPos(), home);
    }

    public static void main(String[] args) throws Exception {
        new DefenderB(pickTeam(args, "B")).run();
    }
}
