@echo off
rem ============================================================
rem  StarSelect Mall - service start/stop script (Windows)
rem
rem  Usage:    svc.bat start^|stop^|status^|restart [target ...]
rem    target = gateway user product cart order admin chat  (7 backend services)
rem             fe   shopping frontend (shop-frontend,  http://localhost:7080)
rem             web  admin frontend    (shop-admin-web,  http://localhost:7090)
rem    no target = all 7 backend services (frontends must be named explicitly)
rem
rem  Requires: JDK 17 in PATH (java + jps), Node 18+ for fe/web
rem  Note:     project path must NOT contain spaces
rem
rem  Examples:
rem    svc.bat start
rem    svc.bat start fe web
rem    svc.bat restart product
rem ============================================================
setlocal

rem ROOT = the directory this script lives in (project root)
for %%i in ("%~dp0.") do set "ROOT=%%~fi"
set "LOG_DIR=%ROOT%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

set "ACTION=%~1"
if "%ACTION%"=="" goto :usage
if /i not "%ACTION%"=="start" if /i not "%ACTION%"=="stop" if /i not "%ACTION%"=="status" if /i not "%ACTION%"=="restart" goto :usage

rem ---- collect targets (skip arg1 = action) ----
set "TARGETS="
:parse
shift
if "%~1"=="" goto :parsed
set "TARGETS=%TARGETS% %~1"
goto :parse
:parsed
if not defined TARGETS set "TARGETS= gateway user product cart order admin chat"

if /i "%ACTION%"=="restart" goto :do_restart
call :dispatch %ACTION%
goto :end

:do_restart
call :dispatch stop
echo ... waiting 10s for graceful shutdown ...
ping -n 11 127.0.0.1 >nul
call :dispatch start
goto :end

:dispatch
rem %~1 = action
for %%t in (%TARGETS%) do (
  if "%%t"=="fe" ( call :%~1_fe
  ) else if "%%t"=="web" ( call :%~1_web
  ) else if "%%t"=="gateway" ( call :%~1_svc gateway
  ) else if "%%t"=="user" ( call :%~1_svc user
  ) else if "%%t"=="product" ( call :%~1_svc product
  ) else if "%%t"=="cart" ( call :%~1_svc cart
  ) else if "%%t"=="order" ( call :%~1_svc order
  ) else if "%%t"=="admin" ( call :%~1_svc admin
  ) else if "%%t"=="chat" ( call :%~1_svc chat
  ) else ( echo [ERR] unknown target: %%t  (valid: gateway user product cart order admin chat fe web) )
)
goto :eof

rem ================= backend services =================

:start_svc
set "svc=%~1"
set "jar=%ROOT%\shop-%svc%\target\shop-%svc%-1.0.0-SNAPSHOT.jar"
if not exist "%jar%" (
  echo [ERR] %svc% jar not found: %jar%   ^(run: mvn clean package -DskipTests^)
  goto :eof
)
jps -l | findstr /C:"shop-%svc%-1.0.0-SNAPSHOT.jar" >nul
if not errorlevel 1 ( echo [SKIP] %svc% already running & goto :eof )
start "shop-%svc%" /MIN cmd /c "java -Xms256m -Xmx512m %SVC_JAVA_OPTS% -jar %jar% > %LOG_DIR%\%svc%.log 2>&1"
echo [START] %svc%  log: %LOG_DIR%\%svc%.log
goto :eof

:stop_svc
set "svc=%~1"
set "found="
for /f "tokens=1" %%p in ('jps -l ^| findstr /C:"shop-%svc%-1.0.0-SNAPSHOT.jar"') do (
  taskkill /PID %%p >nul 2>&1
  echo [STOP] %svc% pid=%%p   (graceful shutdown takes a few seconds)
  set "found=1"
)
if not defined found echo [SKIP] %svc% not running
goto :eof

:status_svc
set "svc=%~1"
jps -l | findstr /C:"shop-%svc%-1.0.0-SNAPSHOT.jar" >nul
if errorlevel 1 ( echo [DOWN] shop-%svc% ) else ( echo [UP]   shop-%svc% )
goto :eof

rem ================= frontends =================
rem Frontends run in their own console window (minimized).
rem Each window keeps the dev server alive: closing the window stops it.

:start_fe
tasklist /FI "WINDOWTITLE eq shop-frontend*" 2>nul | find /i "cmd.exe" >nul
if not errorlevel 1 ( echo [SKIP] fe already running & goto :eof )
start "shop-frontend" /MIN cmd /k "cd /d %ROOT%\shop-frontend && npm run serve > %LOG_DIR%\fe.log 2>&1"
echo [START] shopping frontend (7080)  log: %LOG_DIR%\fe.log
goto :eof

:stop_fe
taskkill /FI "WINDOWTITLE eq shop-frontend*" /T /F >nul 2>&1
if errorlevel 1 ( echo [SKIP] fe not running ) else ( echo [STOP] shopping frontend )
goto :eof

:status_fe
tasklist /FI "WINDOWTITLE eq shop-frontend*" 2>nul | find /i "cmd.exe" >nul
if errorlevel 1 ( echo [DOWN] shopping frontend shop-frontend (7080) ) else ( echo [UP]   shopping frontend shop-frontend (7080) )
goto :eof

:start_web
tasklist /FI "WINDOWTITLE eq shop-admin-web*" 2>nul | find /i "cmd.exe" >nul
if not errorlevel 1 ( echo [SKIP] web already running & goto :eof )
start "shop-admin-web" /MIN cmd /k "cd /d %ROOT%\shop-admin-web && npm run dev > %LOG_DIR%\web.log 2>&1"
echo [START] admin frontend (7090)  log: %LOG_DIR%\web.log
goto :eof

:stop_web
taskkill /FI "WINDOWTITLE eq shop-admin-web*" /T /F >nul 2>&1
if errorlevel 1 ( echo [SKIP] web not running ) else ( echo [STOP] admin frontend )
goto :eof

:status_web
tasklist /FI "WINDOWTITLE eq shop-admin-web*" 2>nul | find /i "cmd.exe" >nul
if errorlevel 1 ( echo [DOWN] admin frontend shop-admin-web (7090) ) else ( echo [UP]   admin frontend shop-admin-web (7090) )
goto :eof

:usage
echo Usage: svc.bat start^|stop^|status^|restart [gateway^|user^|product^|cart^|order^|admin^|chat^|fe^|web]
echo   no target = all 7 backend services; fe = shopping frontend (7080), web = admin frontend (7090)
exit /b 1

:end
endlocal