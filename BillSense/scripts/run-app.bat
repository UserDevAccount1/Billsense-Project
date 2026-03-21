@echo off
REM BillSense - Quick Build & Run
REM Double-click this file or run from terminal to build and launch on emulator
REM Usage: run-app.bat [main|admin]

cd /d "%~dp0\.."

set VARIANT=%1
if "%VARIANT%"=="" set VARIANT=main

echo.
echo ========================================
echo   BillSense Quick Build ^& Run
echo   Variant: %VARIANT%
echo ========================================
echo.

powershell -ExecutionPolicy Bypass -File "%~dp0build-and-run.ps1" -Variant %VARIANT%

pause
