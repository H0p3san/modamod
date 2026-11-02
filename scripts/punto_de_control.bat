@echo off
setlocal EnableDelayedExpansion
rem Punto de control del rediseno: compila, corre motorTest y deja todo en punto_de_control.log (en la raiz del repo).
rem Uso:  scripts\punto_de_control.bat            -> compila y corre las pruebas sin juego
rem       scripts\punto_de_control.bat revision   -> ademas corre runRevision (abre el juego y saca las capturas)
rem El resultado de cada paso sale de lo que imprime Gradle ("BUILD SUCCESSFUL"), no de ERRORLEVEL: el de gradlew.bat
rem no es confiable desde un call.
cd /d "%~dp0\.."
set "LOG=%CD%\punto_de_control.log"
set "PASO=%TEMP%\modamod_paso.log"
echo === %date% %time% > "%LOG%"
git log --oneline -1 >> "%LOG%" 2>&1
git status --short >> "%LOG%" 2>&1

call gradlew.bat build --console=plain --stacktrace > "%PASO%" 2>&1
type "%PASO%" >> "%LOG%"
findstr /c:"BUILD SUCCESSFUL" "%PASO%" >nul && (set "BUILD=OK") || (set "BUILD=FALLO")
echo BUILD=!BUILD! >> "%LOG%"

call gradlew.bat imprimirClasspath --console=plain >> "%LOG%" 2>&1

if /i "%~1"=="revision" (
  call gradlew.bat runRevision --console=plain > "%PASO%" 2>&1
  type "%PASO%" >> "%LOG%"
  findstr /c:"BUILD SUCCESSFUL" "%PASO%" >nul && (set "REVISION=OK") || (set "REVISION=FALLO")
  findstr /c:"[pruebas] FALLO" "%PASO%" >nul && (set "REVISION=!REVISION!, con pruebas fallidas")
  echo REVISION=!REVISION! >> "%LOG%"
)
echo === fin %date% %time% >> "%LOG%"
echo.
echo Build: !BUILD!
if defined REVISION echo Revision: !REVISION!
echo El registro quedo en %LOG%
