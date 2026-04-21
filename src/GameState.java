import java.util.ArrayList;
import java.util.List;

public class GameState {

    public static class Player {
        public int id;
        public String team;
        public double x, y;
        public Vec2 pos() { return new Vec2(x, y); }
    }

    public static class State {
        public Player you;
        public double[] ball;
        public Player[] players;

        public Vec2 ballPos() { return Vec2.of(ball); }
        public Vec2 myPos()   { return you.pos(); }

        public List<Player> teammates(int myId) {
            List<Player> out = new ArrayList<>();
            for (Player p : players) if (p.team.equals(you.team) && p.id != myId) out.add(p);
            return out;
        }

        public List<Player> opponents() {
            List<Player> out = new ArrayList<>();
            for (Player p : players) if (!p.team.equals(you.team)) out.add(p);
            return out;
        }

        public Player closestTo(Vec2 target, List<Player> group) {
            Player best = null;
            double bestD = Double.MAX_VALUE;
            for (Player p : group) {
                double d = p.pos().dist(target);
                if (d < bestD) { bestD = d; best = p; }
            }
            return best;
        }

        public boolean amClosestTeammateToBall(int myId) {
            Vec2 b = ballPos();
            double myD = myPos().dist(b);
            for (Player t : teammates(myId)) {
                if (t.pos().dist(b) < myD - 0.01) return false;
            }
            return true;
        }
    }

    public static class RegisterReq {
        String team;
        public RegisterReq(String team) { this.team = team; }
    }

    public static class RegisterResp {
        public int playerId;
        public String team;
    }
}
