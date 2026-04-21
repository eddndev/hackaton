@echo off
REM Compila todas las clases de src/ a out/
setlocal
cd /d "%~dp0"

if not exist out mkdir out
javac -cp gson-2.10.1.jar -d out src\*.java
if errorlevel 1 (
    echo.
    echo Compilacion fallida.
    exit /b 1
)
echo Compilado -^> out\
endlocal
