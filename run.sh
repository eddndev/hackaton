#!/usr/bin/env bash
# Lanza un bot en foreground (útil para debug).
# Uso:  ./run.sh <NombreClase> [A|B]
# Ejs:  ./run.sh DummyBot A
#       ./run.sh GoalkeeperA A
set -euo pipefail
cd "$(dirname "$0")"

CLASS="${1:?Falta nombre de clase. Ej: ./run.sh GoalkeeperA A}"
TEAM="${2:-}"

exec java -cp "out:gson-2.10.1.jar" "$CLASS" $TEAM
