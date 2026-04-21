import java.util.List;

public class AttackerA extends SoccerBot {

    private static final double SHOOT_MAX_DIST     = 70.0;
    private static final double BLOCKER_RADIUS     = 4.0;
    private static final double PASS_RADIUS        = 3.0;
    private static final double PASS_THRESHOLD     = 0.3;
    private static final double HYSTERESIS         = 0.12;
    private static final double SPEED_PER_TICK     = 2.0;
    private static final int    PRESS_MAX_LOOKAHEAD = 8;
    private static final double REPULSION_DIST    = 10.0;
    private static final double REPULSION_GAIN    = 0.3;
    private static final int    STUCK_THRESHOLD    = 25;

    private String lastAction = "PRESS";
    private final TacticsA.BotMemory mem = new TacticsA.BotMemory();

    public AttackerA(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        Vec2 myPos = s.myPos();
        mem.update(myPos);

        boolean hasBall = canKick(s);
        int rank = teamRank(s);
        TacticsA.Phase phase = TacticsA.detectPhase(s, attacksRight(), FIELD_W);

        if (hasBall || rank == 0) {
            return utilityDecide(s, hasBall);
        }

        Vec2 target;
        switch (phase) {
            case OFFENSE:
                if (mem.isStuck(STUCK_THRESHOLD)) {
                    target = TacticsA.findOpenSpace(s, attacksRight(), FIELD_W, FIELD_H);
                    mem.resetStuck();
                } else {
                    target = TacticsA.overlapPosition(s, attacksRight(), FIELD_W, FIELD_H);
                }
                break;
            case DEFENSE:
                target = TacticsA.passLaneCut(s, ownGoal());
                break;
            default:
                target = supportSlot(s, rank);
        }

        Vec2 push = repulsionFromTeammates(s, REPULSION_DIST);
        return moveToward(myPos, target.add(push.scale(REPULSION_GAIN)));
    }

    private String utilityDecide(GameState.State s, boolean hasBall) {
        double uShoot   = hasBall  ? utilShoot(s)   : 0;
        double uPass    = hasBall  ? utilPass(s)    : 0;
        double uDribble = hasBall  ? utilDribble(s) : 0;
        double uPress   = !hasBall ? utilPress(s)   : 0;
        double uSupport = !hasBall ? utilSupport(s) : 0;

        switch (lastAction) {
            case "SHOOT":   uShoot   += HYSTERESIS; break;
            case "PASS":    uPass    += HYSTERESIS; break;
            case "DRIBBLE": uDribble += HYSTERESIS; break;
            case "PRESS":   uPress   += HYSTERESIS; break;
            case "SUPPORT": uSupport += HYSTERESIS; break;
        }

        double best = -1; String pick = "PRESS";
        if (uShoot   > best) { best = uShoot;   pick = "SHOOT";   }
        if (uPass    > best) { best = uPass;    pick = "PASS";    }
        if (uDribble > best) { best = uDribble; pick = "DRIBBLE"; }
        if (uPress   > best) { best = uPress;   pick = "PRESS";   }
        if (uSupport > best) { best = uSupport; pick = "SUPPORT"; }

        lastAction = pick;

        switch (pick) {
            case "SHOOT":   return kickToward(s.ballPos(), shotAimPoint(s), 5.0);
            case "PASS":    return passAction(s);
            case "DRIBBLE": return kickToward(s.ballPos(), shotAimPoint(s), 2.5);
            case "PRESS":   return moveToward(s.myPos(),
                                              interceptPoint(s.myPos(), SPEED_PER_TICK, PRESS_MAX_LOOKAHEAD));
            case "SUPPORT": return moveToward(s.myPos(), supportSlot(s, 1));
            default:        return moveToward(s.myPos(), s.ballPos());
        }
    }

    private double utilShoot(GameState.State s) {
        Vec2 aim = shotAimPoint(s);
        double dist = s.ballPos().dist(aim);
        double distScore  = Math.max(0, 1.0 - dist / SHOOT_MAX_DIST);
        double clearScore = lineClearance(s.ballPos(), aim, s.opponents(), BLOCKER_RADIUS);
        return distScore * clearScore;
    }

    private double utilPass(GameState.State s) {
        GameState.Player t = bestPassTarget(s);
        if (t == null) return 0;
        double myGoalDist   = s.myPos().dist(opponentGoal());
        double teamGoalDist = t.pos().dist(opponentGoal());
        double improvement  = Math.max(0, (myGoalDist - teamGoalDist) / 100.0);
        double clearance    = lineClearance(s.ballPos(), t.pos(), s.opponents(), PASS_RADIUS);
        return improvement * clearance * 0.9;
    }

    private double utilDribble(GameState.State s) {
        double dist  = s.myPos().dist(opponentGoal());
        double clear = forwardClearance(s);
        return clear * Math.min(1.0, dist / 50.0);
    }

    private double utilPress(GameState.State s) {
        if (!s.amClosestTeammateToBall(s.you.id)) return 0;
        double advanceRatio = attacksRight()
                ? s.ballPos().x / FIELD_W
                : 1.0 - s.ballPos().x / FIELD_W;
        return 0.6 + 0.4 * advanceRatio;
    }

    private double utilSupport(GameState.State s) {
        return s.amClosestTeammateToBall(s.you.id) ? 0 : 0.5;
    }

    private double lineClearance(Vec2 from, Vec2 to, List<GameState.Player> blockers, double radius) {
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

    private double forwardClearance(GameState.State s) {
        Vec2 probe = s.myPos().add(new Vec2(attacksRight() ? 15 : -15, 0));
        return lineClearance(s.myPos(), probe, s.opponents(), BLOCKER_RADIUS);
    }

    private GameState.Player bestPassTarget(GameState.State s) {
        GameState.Player best = null;
        double bestScore = PASS_THRESHOLD;
        double myGoalDist = s.myPos().dist(opponentGoal());
        for (GameState.Player t : s.teammates(s.you.id)) {
            double teamGoalDist = t.pos().dist(opponentGoal());
            if (teamGoalDist >= myGoalDist) continue;
            double clearance = lineClearance(s.ballPos(), t.pos(), s.opponents(), PASS_RADIUS);
            double score = ((myGoalDist - teamGoalDist) / 100.0) * clearance;
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    private String passAction(GameState.State s) {
        GameState.Player t = bestPassTarget(s);
        if (t == null) return kickToward(s.ballPos(), shotAimPoint(s), 5.0);
        double dist  = s.ballPos().dist(t.pos());
        double power = Math.max(1.5, Math.min(4.0, dist / 20.0));
        return kickToward(s.ballPos(), t.pos(), power);
    }

    public static void main(String[] args) throws Exception {
        new AttackerA(pickTeam(args, "A")).run();
    }
}
