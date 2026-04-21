import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public abstract class SoccerBot {

    protected static final double FIELD_W = 175.0;
    protected static final double FIELD_H = 113.0;
    protected static final Vec2   FIELD_CENTER = new Vec2(FIELD_W / 2.0, FIELD_H / 2.0);
    protected static final double GOAL_HALF_HEIGHT = 14.0;

    protected static final int    TICK_MS = 100;
    protected static final String SERVER  =
            System.getenv().getOrDefault("SERVER_URL", "http://localhost:8080");

    protected final String team;
    protected int playerId = -1;
    protected final HttpClient http;
    protected final Gson gson = new Gson();
    protected final BallPredictor predictor = new BallPredictor();

    private final java.util.Map<Integer, Vec2> lastPlayerPos = new java.util.HashMap<>();
    private final java.util.Map<Integer, Vec2> playerVel     = new java.util.HashMap<>();

    public SoccerBot(String team) {
        this.team = team.toUpperCase();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
    }

    public final void run() throws InterruptedException {
        connectWithRetry();
        while (true) {
            long start = System.currentTimeMillis();
            try {
                GameState.State s = fetchState();
                predictor.update(s.ballPos());
                trackPlayerVelocities(s);
                String action = decide(s);
                if (action != null && !action.isBlank()) sendAction(action);
            } catch (Exception e) {
                System.err.printf("[%s id=%d] tick err: %s%n", name(), playerId, e.getMessage());
            }
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = TICK_MS - elapsed;
            if (sleep > 0) Thread.sleep(sleep);
        }
    }

    protected abstract String decide(GameState.State s);

    protected String name() { return getClass().getSimpleName(); }

    protected boolean attacksRight() { return team.equals("A"); }

    protected Vec2 ownGoal() {
        return attacksRight() ? new Vec2(0, FIELD_H / 2.0) : new Vec2(FIELD_W, FIELD_H / 2.0);
    }

    protected Vec2 opponentGoal() {
        return attacksRight() ? new Vec2(FIELD_W, FIELD_H / 2.0) : new Vec2(0, FIELD_H / 2.0);
    }

    protected boolean canKick(GameState.State s) {
        return s.myPos().dist(s.ballPos()) < 1.0;
    }

    protected Vec2 shotAimPoint(GameState.State s) {
        Vec2 goal = opponentGoal();
        GameState.Player rivalGK = null;
        double bestD = Double.MAX_VALUE;
        for (GameState.Player p : s.opponents()) {
            double d = p.pos().dist(goal);
            if (d < bestD) { bestD = d; rivalGK = p; }
        }
        if (rivalGK == null) return goal;
        double sign = rivalGK.y < goal.y ? +1 : -1;
        return new Vec2(goal.x, goal.y + sign * GOAL_HALF_HEIGHT * 0.4);
    }

    protected GameState.Player mostForwardTeammate(GameState.State s) {
        GameState.Player best = null;
        double bestDist = Double.MAX_VALUE;
        Vec2 goal = opponentGoal();
        for (GameState.Player t : s.teammates(s.you.id)) {
            double d = t.pos().dist(goal);
            if (d < bestDist) { bestDist = d; best = t; }
        }
        return best;
    }

    protected boolean teammateHasBall(GameState.State s) {
        for (GameState.Player t : s.teammates(s.you.id)) {
            if (t.pos().dist(s.ballPos()) < 1.0) return true;
        }
        return false;
    }

    protected Vec2 receivePosition(GameState.State s) {
        Vec2 goal = opponentGoal();
        Vec2 ball = s.ballPos();
        double towardGoalX = attacksRight() ? goal.x - 18 : goal.x + 18;
        double y = ball.y < FIELD_H / 2.0 ? FIELD_H * 0.7 : FIELD_H * 0.3;
        return new Vec2(
                Math.max(10, Math.min(FIELD_W - 10, towardGoalX)),
                Math.max(10, Math.min(FIELD_H - 10, y))
        );
    }

    protected Vec2 interceptPoint(Vec2 myPos, double speedPerTick, int maxLookahead) {
        int t = predictor.timeToReach(myPos, speedPerTick, maxLookahead);
        if (t < 0) return predictor.current();
        return predictor.predict(t);
    }

    protected int teamRank(GameState.State s) {
        int myId = s.you.id;
        double myD = s.myPos().dist(s.ballPos());
        int rank = 0;
        for (GameState.Player t : s.teammates(myId)) {
            double d = t.pos().dist(s.ballPos());
            if (d < myD) rank++;
            else if (d == myD && t.id < myId) rank++;
        }
        return rank;
    }

    protected Vec2 supportSlot(GameState.State s, int rank) {
        Vec2 ball = s.ballPos();
        if (rank <= 1) {
            double flankY = ball.y < FIELD_H / 2.0 ? FIELD_H * 0.78 : FIELD_H * 0.22;
            double xOff   = attacksRight() ? 22.0 : -22.0;
            return new Vec2(
                    Math.max(8, Math.min(FIELD_W - 8, ball.x + xOff)),
                    Math.max(8, Math.min(FIELD_H - 8, flankY))
            );
        }
        double idBias   = (s.you.id % 2 == 0 ? 8.0 : -8.0);
        double mirrorY  = FIELD_CENTER.y + (ball.y - FIELD_CENTER.y) * 0.5 + idBias;
        double behindX  = attacksRight()
                ? Math.min(ball.x - 18, FIELD_W / 2.0 - 5)
                : Math.max(ball.x + 18, FIELD_W / 2.0 + 5);
        return new Vec2(
                Math.max(8, Math.min(FIELD_W - 8, behindX)),
                Math.max(8, Math.min(FIELD_H - 8, mirrorY))
        );
    }

    private void trackPlayerVelocities(GameState.State s) {
        for (GameState.Player p : s.players) {
            Vec2 cur  = p.pos();
            Vec2 prev = lastPlayerPos.get(p.id);
            if (prev != null) {
                Vec2 newV = cur.sub(prev);
                Vec2 oldV = playerVel.getOrDefault(p.id, Vec2.zero());
                playerVel.put(p.id, oldV.scale(0.5).add(newV.scale(0.5)));
            }
            lastPlayerPos.put(p.id, cur);
        }
    }

    protected Vec2 playerVelocity(int id) {
        return playerVel.getOrDefault(id, Vec2.zero());
    }

    protected Vec2 leadPosition(GameState.Player target, int ticksAhead) {
        Vec2 v = playerVelocity(target.id);
        if (v.len() < 0.3) return target.pos();
        return target.pos().add(v.scale(ticksAhead));
    }

    protected double kickPowerForDistance(double dist) {
        return Math.max(1.5, Math.min(4.5, dist * 0.14));
    }

    protected double lineClearance(Vec2 from, Vec2 to, java.util.List<GameState.Player> blockers, double radius) {
        Vec2 d = to.sub(from);
        double len = d.len();
        if (len < 1e-6) return 1.0;
        Vec2 u = d.scale(1.0 / len);
        double minPerp = Double.MAX_VALUE;
        for (GameState.Player p : blockers) {
            Vec2 rel = p.pos().sub(from);
            double t = rel.dot(u);
            if (t < 0 || t > len) continue;
            double perp = Math.abs(rel.x * u.y - rel.y * u.x);
            if (perp < minPerp) minPerp = perp;
        }
        if (minPerp == Double.MAX_VALUE) return 1.0;
        if (minPerp < radius) return 0;
        return Math.min(1.0, (minPerp - radius) / radius);
    }

    protected Vec2 repulsionFromTeammates(GameState.State s, double minDist) {
        Vec2 me = s.myPos();
        Vec2 push = Vec2.zero();
        for (GameState.Player t : s.teammates(s.you.id)) {
            Vec2 rel = me.sub(t.pos());
            double d = rel.len();
            if (d < minDist && d > 0.1) {
                push = push.add(rel.scale((minDist - d) / d));
            }
        }
        return push;
    }

    protected String moveToward(Vec2 from, Vec2 target) {
        Vec2 d = target.sub(from).norm();
        return String.format(Locale.ROOT, "MOVE %d %.4f %.4f", playerId, d.x, d.y);
    }

    protected String moveDir(Vec2 unitDir) {
        Vec2 d = unitDir.norm();
        return String.format(Locale.ROOT, "MOVE %d %.4f %.4f", playerId, d.x, d.y);
    }

    protected String stay() {
        return String.format(Locale.ROOT, "MOVE %d 0 0", playerId);
    }

    protected String kickToward(Vec2 ballPos, Vec2 target, double power) {
        Vec2 d = target.sub(ballPos).norm();
        power  = Math.max(0, Math.min(5, power));
        return String.format(Locale.ROOT, "KICK %d %.4f %.4f %.2f", playerId, d.x, d.y, power);
    }

    private void connectWithRetry() throws InterruptedException {
        int attempt = 0;
        while (true) {
            try { register(); return; }
            catch (Exception e) {
                attempt++;
                long wait = Math.min(5000L, 200L * (1L << Math.min(attempt, 5)));
                System.err.printf("[%s] register fail (%d): %s — retry in %dms%n",
                        name(), attempt, e.getMessage(), wait);
                Thread.sleep(wait);
            }
        }
    }

    private void register() throws Exception {
        String json = gson.toJson(new GameState.RegisterReq(team));
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(SERVER + "/register"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(3))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("HTTP " + r.statusCode() + " en /register");
        GameState.RegisterResp resp = gson.fromJson(r.body(), GameState.RegisterResp.class);
        this.playerId = resp.playerId;
        System.out.printf("[%s] registrado id=%d team=%s%n", name(), playerId, team);
    }

    private GameState.State fetchState() throws Exception {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(SERVER + "/state?playerId=" + playerId))
                        .timeout(Duration.ofMillis(500))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400)
            throw new RuntimeException("HTTP " + r.statusCode() + " en /state");
        return gson.fromJson(r.body(), GameState.State.class);
    }

    private void sendAction(String action) throws Exception {
        http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(SERVER + "/action"))
                        .timeout(Duration.ofMillis(500))
                        .POST(HttpRequest.BodyPublishers.ofString(action))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected static String pickTeam(String[] args, String defaultTeam) {
        if (args.length > 0) {
            String t = args[0].toUpperCase();
            if (t.equals("A") || t.equals("B")) return t;
        }
        return defaultTeam;
    }
}
