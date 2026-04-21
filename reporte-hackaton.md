# Hackathon Code Cup Soccer AI — Reporte de planeación

## Contexto

- **Evento**: Hackathon express, 3 horas (12:00-15:00), sala 4102.
- **Formato**: Equipos de 1-2 personas programan bots para partido **3 vs 3** en campo 2D.
- **Arquitectura provista por el organizador**:
  - Servidor maestro con estado del juego.
  - Visualizador en Java (espectador, no controla).
  - Clientes = bots que se conectan vía HTTP (1 conexión por jugador).
- **Todos los jugadores son virtuales**: no hay humanos jugando, solo programando bots.

```
  ┌──────────────┐         ┌──────────────────┐
  │ Visualizador │ ◄────── │                  │ ◄──── Bot Equipo A · Jugador 1
  │   (Java)     │  HTTP   │  Servidor (Master)│ ◄──── Bot Equipo A · Jugador 2
  └──────────────┘  poll   │  - estado juego  │ ◄──── Bot Equipo A · Jugador 3
                           │  - tick loop     │ ◄──── Bot Equipo B · Jugador 1
                           │  - física pelota │ ◄──── Bot Equipo B · Jugador 2
                           └──────────────────┘ ◄──── Bot Equipo B · Jugador 3
                                  ▲
                                  │ POST acción / GET estado
                                  │ (1 conexión por jugador)
```

## División de trabajo recomendada

**Pareja A — Algoritmo / IA del bot**
**Pareja B — Sistemas distribuidos / despliegue**

---

## Pareja A: Algoritmo del bot

### Decisión de stack: Utility AI + heurísticas geométricas

Tres líneas de investigación (algoritmos clásicos RoboCup, ML ligero, frameworks de decisión) convergen en lo mismo:

- **Descartar**: Behavior Trees puros, GOAP, HTN, MCTS, Q-learning, RL pre-entrenado, LLMs como cerebro por tick. Trampas de tiempo o requieren infra inexistente.
- **Adoptar**: **Utility AI** como núcleo de decisión + heurísticas geométricas de alto impacto (interceptación, marcaje, posicionamiento).

### Receta concreta (~250-350 LoC totales)

| Bloque | Técnica | LoC | Tiempo |
|---|---|---|---|
| Roles | SBSP simplificado (home position + atracción a pelota) GK/DEF/ATT | ~30 | 20 min |
| Intercept | Predictor con decay multiplicativo, búsqueda lineal de t óptimo | ~25 | 25 min |
| Coordinación | Closest-to-ball local + locked target con histéresis | ~15 | 10 min |
| Decisión | Utility AI con 5-6 acciones (tirar / pasar / driblar / marcar / cubrir) | ~80 | 50 min |
| Posicionamiento sin pelota | Marcaje hombre-a-hombre + line blocking de pase | ~30 | 25 min |

**Fórmulas clave**:

- Predicción de pelota con fricción multiplicativa:
  `pos(t) = pos0 + vel0 * (1 - decay^t) / (1 - decay)`  con `decay ≈ 0.94`.
- Home position (SBSP):
  `home_pos = base_pos + (ball_pos - field_center) * attraction_factor`
  (defensa: 0.2, medio: 0.5, delantero: 0.8).
- Marcaje:
  `mark_pos = own_goal + (opponent_pos - own_goal).normalized() * (dist - d)`

### Toque de "AI" para el jurado (opcional, +1h)

- **Tiny MLP** (2 capas, 16-32 neuronas) entrenada con *behavior cloning* sobre la heurística → narrativa de "ML supervisado", muestra curvas de loss.
- **Bandit UCB1** sobre 3-4 meta-estrategias (atacar / defender / presionar / contragolpe) por posesión → narrativa de "aprende durante el partido". Más barato y más vistoso que el MLP.

### Lo que NO usar

- **TTM / time-series forecasting**: mal fit, predice valores futuros pero no decide acciones. Una extrapolación lineal o Kalman filter de 20 líneas iguala su precisión en horizontes cortos.
- **RL pre-entrenado (Google Research Football, MuJoCo)**: 8-15h solo para adaptar formato de observación/acción.
- **LLMs por tick**: 50-200ms de latencia mata el ciclo, alucinaciones, sin valor real.

---

## Pareja B: Sistemas y despliegue

### Preguntas obligatorias al organizador (antes del evento)

1. **Tick rate** del servidor (¿20 Hz? ¿60 Hz?) — define presupuesto de cómputo por decisión.
2. **Modelo de sincronía**: ¿el servidor espera la acción de los 6 bots, o avanza solo y usa la última recibida?
3. **Esquema de acciones**: ¿vector continuo de movimiento, dirección discreta, patear con ángulo+fuerza?
4. **Visibilidad**: ¿estado completo del juego, o solo lo que ve cada jugador?
5. **Auth/identidad**: cómo se asocia un cliente HTTP a "jugador 2 del equipo A" (token, nombre, ID).
6. **Acceso previo**: ¿servidor disponible para pruebas antes del día, o solo en sitio?
7. **Latencia de red esperada**: define si las laptops pueden estar fuera de LAN.

### Entregables no negociables

- **Cliente HTTP robusto**: reconnect automático, timeouts, manejo de 5xx. No perder el partido por un socket muerto.
- **Logging por tick**: estado recibido + acción enviada → debug post-mortem y dataset para entrenar el MLP si se opta por esa ruta.
- **Bot dummy local**: random o follow-ball, para probar sin depender del servidor. Gana más tiempo que cualquier optimización posterior.

### Stack sugerido

- **Lenguaje**: Python (`httpx` o `requests` async). Iteración más rápida que Java o JS para esta carga.
- **Estructura**: un proceso por jugador, configuración por env var (`PLAYER_ID`, `TEAM`, `SERVER_URL`).
- **Despliegue local**: `tmux` o un `Makefile` con `make run-team-a` que lanza los 3 procesos simultáneos.

---

## Referencias clave (para credibilidad técnica)

- **Akiyama & Noda**, *HELIOS Base: An Open Source Package for the RoboCup Soccer 2D Simulator* (2013) — referencia canónica de arquitectura.
- **Stone & Veloso**, *Task decomposition, dynamic role assignment, and low-bandwidth communication for real-time strategic teamwork* (AIJ 1999) — role assignment clásico.
- **Reis & Lau**, *FC Portugal Team Description* (RoboCup 2000) — origen de SBSP.
- **Bai, Wu & Chen**, *WrightEagle and UT Austin Villa: RoboCup 2D Champions* (AI Magazine 2015).
- **Dave Mark** — charlas de GDC sobre Utility AI (Kingdoms of Amalur). Base teórica del framework recomendado.
- Repos en GitHub: `helios-base`, `CYRUS2DBase` (C++, muy legibles como referencia de arquitectura).

---

## Cronograma sugerido (3 horas)

| Tiempo | Pareja A (algoritmo) | Pareja B (sistemas) |
|---|---|---|
| 0:00-0:30 | Helpers geométricos (distancia, ángulo, intercept) | Cliente HTTP + parser de estado + logging |
| 0:30-1:00 | Roles + closest-to-ball + intercept predictor | Bot dummy local + script de lanzamiento 3 procesos |
| 1:00-2:00 | Utility AI con 5-6 acciones por rol | Pruebas contra dummy, ajuste de timeouts/reconnect |
| 2:00-2:30 | Tuning de pesos jugando contra dummy | Buffer / soporte a pareja A |
| 2:30-3:00 | Buffer (bugs, opcional MLP o bandit) | Despliegue final, verificación de conexión al server real |
