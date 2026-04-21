#!/usr/bin/env bash
# Lanza los 6 bots en background. Logs -> logs/<Clase>.log
# Ctrl-C mata a todos.
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p logs
CP="out:gson-2.10.1.jar"

BOTS_A=("GoalkeeperA" "DefenderA" "AttackerA")
BOTS_B=("GoalkeeperB" "DefenderB" "AttackerB")

pids=()
launch() {
    local clazz="$1" team="$2"
    java -cp "$CP" "$clazz" "$team" > "logs/${clazz}.log" 2>&1 &
    pids+=($!)
    echo "  $clazz ($team) pid=$!"
}

echo "Lanzando Team A..."
for c in "${BOTS_A[@]}"; do launch "$c" A; done
echo "Lanzando Team B..."
for c in "${BOTS_B[@]}"; do launch "$c" B; done

cleanup() {
    echo
    echo "Matando bots..."
    kill "${pids[@]}" 2>/dev/null || true
    wait 2>/dev/null || true
    exit 0
}
trap cleanup INT TERM

echo
echo "6 bots corriendo. Ctrl-C para detener. Logs en logs/"
wait
