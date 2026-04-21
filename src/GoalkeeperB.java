public class GoalkeeperB extends SoccerBot {

    public GoalkeeperB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        if (canKick(s)) return kickToward(s.ballPos(), shotAimPoint(s), 5.0); // Despejar con todo

        Vec2 ball = s.ballPos();
        Vec2 myGoal = ownGoal();

        // Si el balón entra al área chica (dist < 20), romper línea e interceptar
        if (myGoal.dist(ball) < 20.0) {
            return moveToward(s.myPos(), ball);
        }

        // Quedarse pegado a la portería siguiendo la altura (Y) del balón
        double targetX = myGoal.x + (attacksRight() ? 3.0 : -3.0); 
        // Clamp para no salirse de los postes de la portería
        double targetY = Math.max(FIELD_CENTER.y - GOAL_HALF_HEIGHT, 
                         Math.min(FIELD_CENTER.y + GOAL_HALF_HEIGHT, ball.y)); 
        
        return moveToward(s.myPos(), new Vec2(targetX, targetY));
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperB(pickTeam(args, "B")).run();
    }
}
