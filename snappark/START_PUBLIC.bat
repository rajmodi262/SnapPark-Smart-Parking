@echo off
title SnapPark — PUBLIC MODE (Internet Access)
color 0B
echo.
echo  ================================================
echo   SNAPPARK — PUBLIC MODE (No WiFi needed!)
echo   Phones use their own 5G/4G to access the app
echo  ================================================
echo.

cd /d "%~dp0"

:: ── Kill any existing ngrok ──
taskkill /F /IM ngrok.exe >nul 2>&1

:: ── Kill anything on port 8080 ──
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTEN" 2^>nul') do (
    taskkill /F /PID %%a >nul 2>&1
)

:: ── Check tools ──
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Maven not found!
    pause
    exit /b 1
)
where ngrok >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] ngrok not found! Install: winget install ngrok.ngrok
    pause
    exit /b 1
)

:: ── Compile ──
echo  [1/3] Compiling...
call mvn compile -q 2>nul
if %ERRORLEVEL% neq 0 (
    call mvn clean compile -q
)
echo  [OK] Build successful!

:: ── Start ngrok in background ──
echo  [2/3] Starting ngrok tunnel...
start /B ngrok http 8080 --log=stdout >"%~dp0ngrok_log.txt" 2>&1

:: Wait for ngrok to start and get the URL
echo  [*] Waiting for public URL...
set NGROK_URL=
set RETRIES=0

:wait_ngrok
timeout /t 2 /nobreak >nul
set /a RETRIES+=1

:: Query ngrok API and extract public_url in one step to avoid json quote escaping issues
for /f "usebackq tokens=*" %%u in (`powershell -NoProfile -Command "$t = (Invoke-WebRequest -Uri 'http://127.0.0.1:4040/api/tunnels' -UseBasicParsing -ErrorAction SilentlyContinue).Content | ConvertFrom-Json; if ($t -and $t.tunnels.Count -gt 0) { $t.tunnels[0].public_url }" 2^>nul`) do set NGROK_URL=%%u

if "%NGROK_URL%"=="" (
    if %RETRIES% LSS 10 goto :wait_ngrok
    echo  [WARN] Could not get ngrok URL. Falling back to LAN mode.
    set NGROK_URL=
)

if not "%NGROK_URL%"=="" (
    echo  [OK] PUBLIC URL: %NGROK_URL%
) else (
    echo  [WARN] Running in LAN-only mode.
)

:: ── Launch app with public URL ──
echo  [3/3] Launching SnapPark...
echo.
echo  =====================================================
if not "%NGROK_URL%"=="" (
    echo   PUBLIC ACCESS (any phone, any network!^):
    echo     Entry:   %NGROK_URL%/mobile/entry.html
    echo     Exit:    %NGROK_URL%/mobile/exit.html
    echo     History: %NGROK_URL%/mobile/history.html
) else (
    echo   LAN ACCESS ONLY (same WiFi^):
    echo     Entry:   http://localhost:8080/mobile/entry.html
)
echo  =====================================================
echo.

if not "%NGROK_URL%"=="" (
    call mvn javafx:run -Dexec.args="" -Dsnappark.publicUrl="%NGROK_URL%"
) else (
    call mvn javafx:run
)

:: Cleanup
taskkill /F /IM ngrok.exe >nul 2>&1
echo  SnapPark stopped.
pause
