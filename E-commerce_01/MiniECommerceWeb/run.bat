@echo off
cd /d "%~dp0"
if not exist out mkdir out
echo Compiling...
javac -d out src\*.java
if errorlevel 1 (
  echo.
  echo Compile failed. Make sure a JDK 17 or newer is installed: run "javac -version"
  pause
  exit /b 1
)
echo Starting server. Your browser will open at http://localhost:8080
echo Press Ctrl+C to stop.
java -cp out Main
pause
