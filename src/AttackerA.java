import java.util.concurrent.ThreadLocalRandom;

public class AttackerA extends SoccerBot {

    private static final double SHOOT_MAX_DIST     = 80.0;
    private static final double SHOT_BLOCKER_RADIUS = 2.5;
    private static final double PASS_BLOCKER_RADIUS = 4.0;
    private static final double PASS_RADIUS        = 3.0;
    private static final double PASS_THRESHOLD     = 0.1;
    private static final double HYSTERESIS         = 0.12;
    private static final double TEMPERATURE        = 0.08;
    private static final double CLOSE_RANGE        = 35.0;
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
        if (teammateHasBall(s)) {
            target = receivePosition(s);
        } else {
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
        }

        Vec2 push = repulsionFromTeammates(s, REPULSION_DIST);
        return moveToward(myPos, target.add(push.scale(REPULSION_GAIN)));
    }

    private String utilityDecide(GameState.State s, boolean hasBall) {
        double uShoot   = (hasBall  ? utilShoot(s)   : 0) + noise();
        double uPass    = (hasBall  ? utilPass(s)    : 0) + noise();
        double uDribble = (hasBall  ? utilDribble(s) : 0) + noise();
        double uRecycle = (hasBall  ? utilRecycle(s) : 0) + noise();
        double uPress   = (!hasBall ? utilPress(s)   : 0) + noise();
        double uSupport = (!hasBall ? utilSupport(s) : 0) + noise();

        switch (lastAction) {
            case "SHOOT":   uShoot   += HYSTERESIS; break;
            case "PASS":    uPass    += HYSTERESIS; break;
            case "DRIBBLE": uDribble += HYSTERESIS; break;
            case "RECYCLE": uRecycle += HYSTERESIS; break;
            case "PRESS":   uPress   += HYSTERESIS; break;
            case "SUPPORT": uSupport += HYSTERESIS; break;
        }

        double best = -1; String pick = "PRESS";
        if (uShoot   > best) { best = uShoot;   pick = "SHOOT";   }
        if (uPass    > best) { best = uPass;    pick = "PASS";    }
        if (uDribble > best) { best = uDribble; pick = "DRIBBLE"; }
        if (uRecycle > best) { best = uRecycle; pick = "RECYCLE"; }
        if (uPress   > best) { best = uPress;   pick = "PRESS";   }
        if (uSupport > best) { best = uSupport; pick = "SUPPORT"; }

        lastAction = pick;

        switch (pick) {
            case "SHOOT":   return shootAction(s);
            case "PASS":    return passAction(s);
            case "DRIBBLE": return kickToward(s.ballPos(), shotAimPoint(s), 2.5);
            case "RECYCLE": return recycleAction(s);
            case "PRESS":   return moveToward(s.myPos(),
                                              interceptPoint(s.myPos(), SPEED_PER_TICK, PRESS_MAX_LOOKAHEAD));
            case "SUPPORT": return moveToward(s.myPos(), supportSlot(s, 1));
            default:        return moveToward(s.myPos(), s.ballPos());
        }
    }

    private double utilRecycle(GameState.State s) {
        Vec2 aim = shotAimPoint(s);
        double shootClear = lineClearance(s.ballPos(), aim, s.opponents(), SHOT_BLOCKER_RADIUS);
        double fwdClear   = forwardClearance(s);
        if (shootClear > 0.3 || fwdClear > 0.5) return 0;
        GameState.Player back = safestBackwardTeammate(s);
        if (back == null) return 0;
        double clearance = lineClearance(s.ballPos(), back.pos(), s.opponents(), PASS_BLOCKER_RADIUS);
        return clearance * 0.55;
    }

    private String recycleAction(GameState.State s) {
        GameState.Player back = safestBackwardTeammate(s);
        if (back == null) return shootAction(s);
        Vec2 leadTo = leadPosition(back, 3);
        double dist = s.ballPos().dist(leadTo);
        return kickToward(s.ballPos(), leadTo, kickPowerForDistance(dist));
    }

    private String shootAction(GameState.State s) {
        return kickToward(s.ballPos(), shotAimPoint(s), 5.0);
    }

    private double noise() {
        return ThreadLocalRandom.current().nextDouble() * TEMPERATURE;
    }

    private double utilShoot(GameState.State s) {
        Vec2 aim = shotAimPoint(s);
        double dist = s.ballPos().dist(aim);
        double distScore  = Math.max(0, 1.0 - dist / SHOOT_MAX_DIST);
        double clearScore = lineClearance(s.ballPos(), aim, s.opponents(), SHOT_BLOCKER_RADIUS);
        double closeBonus = dist < CLOSE_RANGE ? 0.3 * (1.0 - dist / CLOSE_RANGE) : 0;
        return distScore * clearScore + closeBonus * clearScore;
    }

    private double utilPass(GameState.State s) {
        GameState.Player t = bestPassTarget(s, PASS_THRESHOLD);
        if (t == null) return 0;
        double myGoalDist   = s.myPos().dist(opponentGoal());
        double teamGoalDist = t.pos().dist(opponentGoal());
        double improvement  = Math.min(1.0, Math.max(0, (myGoalDist - teamGoalDist) / 50.0));
        double dist         = s.ballPos().dist(t.pos());
        double proximity    = Math.max(0, 1.0 - dist / 45.0);
        double clearance    = lineClearance(s.ballPos(), t.pos(), s.opponents(), PASS_BLOCKER_RADIUS);
        return Math.min(1.0, (improvement + proximity * 0.4) * clearance * 1.1);
    }

    private double utilDribble(GameState.State s) {
        double dist  = s.myPos().dist(opponentGoal());
        double clear = forwardClearance(s);
        return clear * Math.min(1.0, dist / 50.0) * 0.75;
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

    private double forwardClearance(GameState.State s) {
        Vec2 probe = s.myPos().add(new Vec2(attacksRight() ? 15 : -15, 0));
        return lineClearance(s.myPos(), probe, s.opponents(), PASS_BLOCKER_RADIUS);
    }

    private String passAction(GameState.State s) {
        GameState.Player t = bestPassTarget(s, PASS_THRESHOLD);
        if (t == null) return shootAction(s);
        Vec2   leadTo = leadPosition(t, 3);
        double dist   = s.ballPos().dist(leadTo);
        double power  = kickPowerForDistance(dist);
        return kickToward(s.ballPos(), leadTo, power);
    }

    public static void main(String[] args) throws Exception {
        new AttackerA(pickTeam(args, "A")).run();
    }
}
