@echo off
REM Mata los 6 bots lanzados por run-all.bat (filtra por titulo de ventana).
REM No toca el servidor ni el visualizador.
echo Deteniendo los 6 bots...
taskkill /F /FI "WINDOWTITLE eq GoalkeeperA" 2>nul
taskkill /F /FI "WINDOWTITLE eq DefenderA"   2>nul
taskkill /F /FI "WINDOWTITLE eq AttackerA"   2>nul
taskkill /F /FI "WINDOWTITLE eq GoalkeeperB" 2>nul
taskkill /F /FI "WINDOWTITLE eq DefenderB"   2>nul
taskkill /F /FI "WINDOWTITLE eq AttackerB"   2>nul
echo Listo.
