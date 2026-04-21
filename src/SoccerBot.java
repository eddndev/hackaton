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
        return new Vec2(goal.x, goal.y + sign * GOAL_HALF_HEIGHT * 0.8);
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
            double yOff  = ball.y < FIELD_H / 2.0 ? 22.0 : -22.0;
            double xOff  = attacksRight() ? 18.0 : -18.0;
            return new Vec2(
                    Math.max(8, Math.min(FIELD_W - 8, ball.x + xOff)),
                    Math.max(8, Math.min(FIELD_H - 8, ball.y + yOff))
            );
        }
        double behindX = attacksRight()
                ? Math.min(ball.x - 20, FIELD_W / 2.0 - 5)
                : Math.max(ball.x + 20, FIELD_W / 2.0 + 5);
        double coverY = FIELD_CENTER.y + (ball.y - FIELD_CENTER.y) * 0.5;
        return new Vec2(
                Math.max(8, Math.min(FIELD_W - 8, behindX)),
                Math.max(8, Math.min(FIELD_H - 8, coverY))
        );
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
