@echo off
title SnapPark — Smart Parking System
color 0A
echo.
echo  ========================================
echo       SNAPPARK — Smart Parking System
echo  ========================================
echo.

cd /d "%~dp0"

:: ── Check Maven ──
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven not found! Install Maven and add to PATH.
    pause
    exit /b 1
)

:: ── Detect IP ──
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /c:"IPv4"') do (
    set "LANIP=%%i"
    goto :gotip
)
:gotip
set LANIP=%LANIP: =%

:: ── Build ──
echo  [1/2] Compiling...
call mvn compile -q 2>nul
if %ERRORLEVEL% neq 0 (
    call mvn clean compile -q
    if %ERRORLEVEL% neq 0 (
        echo  [ERROR] Build failed!
        pause
        exit /b 1
    )
)
echo  [OK] Build successful!

:: ── Launch ──
echo.
echo  [2/2] Launching SnapPark...
echo.
echo  =====================================================
echo   KIOSK: Fullscreen window on this PC
echo.
echo   MOBILE (open on phone, same WiFi):
echo     Entry:   http://%LANIP%:8080/mobile/entry.html
echo     Exit:    http://%LANIP%:8080/mobile/exit.html
echo     History: http://%LANIP%:8080/mobile/history.html
echo.
echo   Phone MUST be on same WiFi as this PC!
echo  =====================================================
echo.

call mvn javafx:run

echo.
echo  SnapPark stopped.
pause
