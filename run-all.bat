@echo off
REM Lanza los 6 bots en ventanas separadas (una por bot).
REM Cada ventana muestra el stdout del bot en tiempo real.
REM Para detener todos: usa stop-all.bat o cierra cada ventana.
setlocal
cd /d "%~dp0"

if not exist logs mkdir logs
set "CP=out;gson-2.10.1.jar"

echo Lanzando Team A...
start "GoalkeeperA" cmd /k "java -cp %CP% GoalkeeperA A"
start "DefenderA"   cmd /k "java -cp %CP% DefenderA A"
start "AttackerA"   cmd /k "java -cp %CP% AttackerA A"

echo Lanzando Team B...
start "GoalkeeperB" cmd /k "java -cp %CP% GoalkeeperB B"
start "DefenderB"   cmd /k "java -cp %CP% DefenderB B"
start "AttackerB"   cmd /k "java -cp %CP% AttackerB B"

echo.
echo 6 bots lanzados en ventanas separadas.
echo Para detenerlos: stop-all.bat (o cierra cada ventana).
endlocal
