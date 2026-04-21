#!/usr/bin/env bash
# Compila todas las clases de src/ a out/
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p out
javac -cp gson-2.10.1.jar -d out src/*.java
echo "Compilado -> out/"
