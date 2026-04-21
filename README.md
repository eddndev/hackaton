# Hackathon Code Cup Soccer AI — 3v3

Bots para el simulador 2D de soccer del hackathon de ESCOM-IPN. Servidor y visualizador vienen como JAR; los bots son clientes HTTP en Java.

Repo compartido entre dos personas. La infraestructura (`SoccerBot`, `Vec2`, `GameState`, `BallPredictor`) ya está escrita y probada — **no hace falta tocarla**. Cada quien implementa los 3 bots de su equipo llenando el método `decide()`.

---

## Repartición

| Persona | Equipo | Enfoque | Archivos a llenar |
|---|---|---|---|
| **eddndev** | **A** (azul, ataca →) | Utility AI + heurísticas geométricas | `src/GoalkeeperA.java`, `src/DefenderA.java`, `src/AttackerA.java` |
| **RobertLopez893** | **B** (rojo, ataca ←) | ML ligero (Bandit UCB1 / MLP) | `src/GoalkeeperB.java`, `src/DefenderB.java`, `src/AttackerB.java` |

Los stubs ya compilan y corren con comportamiento dummy (van al balón y patean). Se especializa uno a la vez.

---

## Arranque rápido

### Linux / macOS

```bash
./compile.sh                 # compila src/*.java -> out/
java -jar server.jar         # terminal 1
java -jar visualizador.jar   # terminal 2
./run-all.sh                 # terminal 3: lanza los 6 bots, logs en logs/
```

Debug un bot: `./run.sh GoalkeeperB B`

### Windows

```bat
compile.bat                  REM compila src\*.java -> out\
java -jar server.jar         REM terminal 1
java -jar visualizador.jar   REM terminal 2
run-all.bat                  REM abre 6 ventanas, una por bot
stop-all.bat                 REM mata los 6 bots por titulo de ventana
```

Debug un bot: `run.bat GoalkeeperB B`

Click en **Iniciar Partido** en el visualizador.

---

## Cómo se escribe un bot

Cada bot extiende `SoccerBot` y sobrescribe **un solo método**:

```java
public class GoalkeeperB extends SoccerBot {
    public GoalkeeperB(String team) { super(team); }

    @Override
    protected String decide(GameState.State s) {
        // tu lógica aquí; debe devolver un string MOVE o KICK
        return moveToward(s.myPos(), s.ballPos());
    }

    public static void main(String[] args) throws Exception {
        new GoalkeeperB(pickTeam(args, "B")).run();
    }
}
```

El `run()` ya maneja registro, loop de 100 ms, reconexión con backoff y try/catch por tick. **No tocar los métodos privados de `SoccerBot`** — rompería el contrato con el otro equipo.

### Helpers que tienes disponibles

**Estado del juego (`GameState.State s`)**:

| Método | Devuelve |
|---|---|
| `s.myPos()` | `Vec2` — mi posición |
| `s.ballPos()` | `Vec2` — posición del balón |
| `s.you.id`, `s.you.team` | mi id y equipo |
| `s.teammates(playerId)` | `List<Player>` — compañeros (excluye a mí) |
| `s.opponents()` | `List<Player>` — rivales |
| `s.closestTo(pos, list)` | `Player` más cercano a `pos` |
| `s.amClosestTeammateToBall(playerId)` | `true` si soy el más cercano al balón de mi equipo |

**Predicción del balón (`predictor`)**:

| Método | Descripción |
|---|---|
| `predictor.current()` | `Vec2` — última posición conocida |
| `predictor.velocity()` | `Vec2` — velocidad por tick (Δpos) |
| `predictor.predict(ticks)` | `Vec2` — posición estimada `N` ticks adelante (decay 0.94) |
| `predictor.timeToReach(from, speedPerTick, maxTicks)` | ticks para interceptar, `-1` si no llega |

**Geometría del campo** (constantes en `SoccerBot`):

- `FIELD_W = 175.0`, `FIELD_H = 113.0`, `FIELD_CENTER = (87.5, 56.5)`
- `GOAL_HALF_HEIGHT = 14.0` (estimación; tunear)
- `ownGoal()`, `opponentGoal()` — `Vec2` del centro de cada portería

**Acciones** (devuelven el string listo para enviar):

| Método | Uso |
|---|---|
| `moveToward(from, target)` | mover en dirección `target - from` (normalizado) |
| `moveDir(unitVec)` | mover en un vector unitario directo |
| `stay()` | `MOVE id 0 0` |
| `kickToward(ballPos, target, power)` | `KICK` hacia `target`, `power ∈ [0,5]`, solo válido si `canKick(s)` |
| `canKick(s)` | `true` si mi distancia al balón es `< 1.0` |

**Clase `Vec2`**: `add`, `sub`, `scale`, `dot`, `norm`, `len`, `dist`, `clamp`.

---

## Sistema de coordenadas

- Campo **175 × 113**, origen **arriba-izquierda**, Y+ hacia abajo (Swing).
- Team **A** (azul): portería propia en **x ≈ 0**, ataca hacia **+x**.
- Team **B** (rojo): portería propia en **x ≈ 174**, ataca hacia **-x**.
- `ownGoal()` / `opponentGoal()` ya resuelven esto automáticamente según el equipo.

---

## Protocolo del servidor (referencia)

Ya lo abstrae `SoccerBot`, pero por si necesitas depurar:

- `POST /register` body `{"team":"A"|"B"}` → `{playerId, team}`
- `GET /state?playerId=X` → `{you, ball:[x,y], players:[...]}`
- `POST /action` body texto: `MOVE <id> <dx> <dy>` o `KICK <id> <dx> <dy> <power>`
- Tick = 100 ms. KICK solo válido si `dist(jugador, balón) < 1`. Power ∈ `[0, 5]`.

Ver `Hackathon Code Cup Soccer AI.pdf` y `reporte-hackaton.md` para el detalle completo.

---

## Sugerencias por rol (Team B — enfoque ML)

### `GoalkeeperB` — reactivo simple

Se queda pegado a la portería siguiendo el balón:

```
target.x = ownGoal().x + (attacksRight() ? +3 : -3)   // un poco dentro del área
target.y = clamp(ballPos.y, center ± GOAL_HALF_HEIGHT)
return moveToward(myPos, target);
```

Si el balón entra al área chica (dist a `ownGoal() < 20`), romper la línea e ir a interceptar.

### `DefenderB` — zone defense (SBSP puro)

Home position sin marcaje individual:

```
base  = attacksRight() ? (30,  ownGoalY±10) : (145, ownGoalY±10)
home  = base + (ballPos - FIELD_CENTER) * 0.25
if (amClosestTeammateToBall && ballPos en mi mitad) ir al balón
else moveToward(myPos, home)
```

### `AttackerB` — Bandit UCB1 sobre meta-estrategias

Cada **N ticks** (p.ej. N=30, cada 3 segundos), el bandit elige una meta-estrategia y la mantiene:

1. **ATTACK**: ir al balón, patear a `opponentGoal()` a power 5
2. **SUPPORT**: posicionarse a `(opponentGoal().x - 20, ballPos.y ± 15)` esperando pase
3. **PRESS**: acosar al rival con el balón (ir a su posición actual)
4. **COUNTER**: quedarse adelantado cerca de `(opponentGoal().x - 10, center.y)`

**UCB1**: para cada brazo `i` con `n_i` usos y recompensa media `r̄_i`:

```
score_i = r̄_i + c * sqrt(ln(N_total) / n_i)
```

Elegir `argmax`. `c ≈ √2`. Recompensa observable: **+1 si gol a favor, -1 en contra, 0 si no pasa nada**. El servidor no expone el score directamente, hay que detectarlo por cambios bruscos de posición del balón al centro (reset tras gol) — o simplificar a recompensa heurística (p.ej. `+0.1` si el balón se movió hacia `opponentGoal()` desde el último tick).

Alternativa más simple si el tiempo aprieta: **tiny MLP** (entrada `[myPos, ballPos, ballVel, nearestOpp, distToGoal]`, salida `[dx, dy, kickPower]`) entrenada con *behavior cloning* sobre un log de partidas del dummy.

---

## Git workflow

```bash
git pull --rebase origin main       # siempre antes de empezar
# ... implementar en tus 3 archivos ...
./compile.sh                         # verificar que compila
git add src/GoalkeeperB.java src/DefenderB.java src/AttackerB.java
git commit -m "Team B: <lo que hiciste>"
git push
```

Como trabajan en archivos separados (tú tocas `*B.java`, el otro `*A.java`), no debería haber conflictos. La infra (`SoccerBot`, `Vec2`, `GameState`, `BallPredictor`) es **read-only** para ambos — si necesitas un helper nuevo, avisa antes de cambiarla.
