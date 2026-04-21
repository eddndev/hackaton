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
