@echo off
REM Lanza un bot en foreground (util para debug).
REM Uso:  run.bat ^<NombreClase^> [A^|B]
REM Ej:   run.bat GoalkeeperB B
setlocal
cd /d "%~dp0"

if "%~1"=="" (
    echo Uso: run.bat ^<NombreClase^> [A^|B]
    echo Ej:  run.bat GoalkeeperB B
    exit /b 1
)

java -cp "out;gson-2.10.1.jar" %1 %2
endlocal
