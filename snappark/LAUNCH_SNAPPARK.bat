@echo off
title SnapPark — Smart Launcher
color 0A

echo.
echo  ╔══════════════════════════════════════════════════╗
echo  ║       SNAPPARK — SMART LAUNCHER                 ║
echo  ║   Wakes DB, starts servers, launches kiosk       ║
echo  ╚══════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

:: ══════════════════════════════════════════════════════
::  STEP 1: Kill old processes
:: ══════════════════════════════════════════════════════
echo  [1/5] Cleaning up old processes...
taskkill /F /IM ngrok.exe >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTEN" 2^>nul') do (
    echo        Killing PID %%a on port 8080...
    taskkill /F /PID %%a >nul 2>&1
)
echo  [OK] Ports cleared!

:: ══════════════════════════════════════════════════════
::  STEP 2: Check required tools
:: ══════════════════════════════════════════════════════
echo.
echo  [2/5] Checking tools...
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven not found! Install Maven and add to PATH.
    pause
    exit /b 1
)
echo        Maven ✓

where ngrok >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo  [WARN] ngrok not found. Will run in LAN-only mode.
    echo         Install: winget install ngrok.ngrok
    set HAS_NGROK=0
) else (
    echo        ngrok ✓
    set HAS_NGROK=1
)

:: ══════════════════════════════════════════════════════
::  STEP 3: Wake up Neon Cloud Database
:: ══════════════════════════════════════════════════════
echo.
echo  [3/5] Waking up Neon Cloud Database...
echo        (Free tier suspends after 5min idle — may take up to 30s)
echo.

:: Compile first so we have the PostgreSQL driver
call mvn compile -q 2>nul
if %ERRORLEVEL% neq 0 (
    call mvn clean compile -q
    if %ERRORLEVEL% neq 0 (
        echo  [ERROR] Build failed!
        pause
        exit /b 1
    )
)

:: Run the DB wake-up/seeder script
call mvn exec:java "-Dexec.mainClass=SeedSlots" -q 2>nul
if %ERRORLEVEL% equ 0 (
    echo  [OK] Database is AWAKE and has slots!
) else (
    echo  [WARN] First wake attempt slow... Retrying (Neon cold start^)...
    timeout /t 5 /nobreak >nul
    call mvn exec:java "-Dexec.mainClass=SeedSlots" -q 2>nul
    if %ERRORLEVEL% equ 0 (
        echo  [OK] Database is AWAKE on retry!
    ) else (
        echo  [WARN] DB may still be waking up. App will retry automatically.
    )
)

:: ══════════════════════════════════════════════════════
::  STEP 4: Start ngrok tunnel
:: ══════════════════════════════════════════════════════
echo.
set NGROK_URL=
if "%HAS_NGROK%"=="1" (
    echo  [4/5] Starting ngrok tunnel...
    start /B ngrok http 8080 --log=stdout >"%~dp0ngrok_log.txt" 2>&1
    echo        Waiting for public URL...
    set RETRIES=0
) else (
    echo  [4/5] Skipping ngrok (not installed). LAN mode only.
    goto skip_ngrok
)

:wait_ngrok
timeout /t 2 /nobreak >nul
set /a RETRIES+=1

for /f "usebackq tokens=*" %%u in (`powershell -NoProfile -Command "$t = (Invoke-WebRequest -Uri 'http://127.0.0.1:4040/api/tunnels' -UseBasicParsing -ErrorAction SilentlyContinue).Content | ConvertFrom-Json; if ($t -and $t.tunnels.Count -gt 0) { $t.tunnels[0].public_url }" 2^>nul`) do set NGROK_URL=%%u

if "%NGROK_URL%"=="" (
    if %RETRIES% LSS 10 goto :wait_ngrok
    echo  [WARN] Could not get ngrok URL. Falling back to LAN mode.
)

if not "%NGROK_URL%"=="" (
    echo  [OK] PUBLIC URL: %NGROK_URL%
)

:skip_ngrok

:: ══════════════════════════════════════════════════════
::  STEP 5: Launch SnapPark Kiosk
:: ══════════════════════════════════════════════════════
echo.
echo  [5/5] Launching SnapPark Kiosk...
echo.
echo  ╔══════════════════════════════════════════════════════╗
if not "%NGROK_URL%"=="" (
    echo  ║  PUBLIC ACCESS (any phone, any network!^)           ║
    echo  ╠══════════════════════════════════════════════════════╣
    echo  ║  Entry:   %NGROK_URL%/mobile/entry.html
    echo  ║  Exit:    %NGROK_URL%/mobile/exit.html
    echo  ║  History: %NGROK_URL%/mobile/history.html
) else (
    echo  ║  LAN ACCESS ONLY (same WiFi needed^)               ║
    echo  ╠══════════════════════════════════════════════════════╣
    echo  ║  Entry:   http://localhost:8080/mobile/entry.html   ║
    echo  ║  Exit:    http://localhost:8080/mobile/exit.html     ║
)
echo  ╚══════════════════════════════════════════════════════╝
echo.

if not "%NGROK_URL%"=="" (
    call mvn javafx:run -Dexec.args="" -Dsnappark.publicUrl="%NGROK_URL%"
) else (
    call mvn javafx:run
)

:: ══════════════════════════════════════════════════════
::  Cleanup on exit
:: ══════════════════════════════════════════════════════
taskkill /F /IM ngrok.exe >nul 2>&1
echo.
echo  SnapPark stopped. All services cleaned up.
pause
