@echo off
rem Punto de control del rediseno: compila, corre motorTest y deja todo en punto_de_control.log (en la raiz del repo).
rem Uso:  scripts\punto_de_control.bat            -> compila y corre las pruebas sin juego
rem       scripts\punto_de_control.bat revision   -> ademas corre runRevision (abre el juego y saca las capturas)
cd /d "%~dp0\.."
set "LOG=%CD%\punto_de_control.log"
echo === %date% %time% > "%LOG%"
git log --oneline -1 >> "%LOG%" 2>&1
git status --short >> "%LOG%" 2>&1
call gradlew.bat build --console=plain --stacktrace >> "%LOG%" 2>&1
echo BUILD_EXIT=%ERRORLEVEL% >> "%LOG%"
if /i "%~1"=="revision" (
  call gradlew.bat runRevision --console=plain >> "%LOG%" 2>&1
  echo REVISION_EXIT=%ERRORLEVEL% >> "%LOG%"
)
echo === fin %date% %time% >> "%LOG%"
echo Listo. El registro quedo en %LOG%
