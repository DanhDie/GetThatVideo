@echo off
title GetThatVideo - Kotlin Multiplatform Desktop
echo Compilando e iniciando o aplicativo Kotlin Multiplatform (Desktop JVM)...
call "%~dp0gradlew.bat" :composeApp:run
pause
