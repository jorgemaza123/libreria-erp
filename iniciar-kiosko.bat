@echo off
chcp 65001 >nul
title Sistema ERP - Iniciando...

:: ============================================================
:: PASO 1: Cerrar Chrome si ya está corriendo
:: ============================================================
echo [1/3] Cerrando instancias de Chrome...
taskkill /F /IM chrome.exe /T >nul 2>&1
timeout /t 2 /nobreak >nul

:: ============================================================
:: PASO 2: Limpiar archivos de sesion del perfil kiosko
:: Solo borra los datos de sesion, no el perfil completo.
:: Esto garantiza que Chrome no restaure la ultima pestaña.
:: ============================================================
echo [2/3] Limpiando sesion anterior...
set KIOSK_PROFILE=%LOCALAPPDATA%\Google\Chrome\KioskoERP

if exist "%KIOSK_PROFILE%\Default\Sessions" (
    rmdir /s /q "%KIOSK_PROFILE%\Default\Sessions" >nul 2>&1
)
del /f /q "%KIOSK_PROFILE%\Default\Last Session"    >nul 2>&1
del /f /q "%KIOSK_PROFILE%\Default\Last Tabs"       >nul 2>&1
del /f /q "%KIOSK_PROFILE%\Default\Current Session" >nul 2>&1
del /f /q "%KIOSK_PROFILE%\Default\Current Tabs"    >nul 2>&1

:: ============================================================
:: PASO 3: Lanzar Chrome en modo kiosko con perfil aislado
::
:: Flags clave:
::   --kiosk                         Pantalla completa, sin barra
::   --app=URL                       Abre directo en la URL, sin tabs
::   --user-data-dir                 Perfil exclusivo, aislado del Chrome normal
::   --no-restore-last-session       No restaura sesion tras crash/apagado
::   --no-first-run                  Omite el wizard de bienvenida
::   --disable-session-crashed-bubble  No muestra popup de "Chrome no cerro bien"
::   --disable-sync                  No sincroniza con cuenta Google
::   --ignore-certificate-errors     Para certificados autofirmados (HTTPS local)
::   --allow-insecure-localhost      Permite localhost con SSL autofirmado
:: ============================================================
echo [3/3] Iniciando sistema...

set CHROME="C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% set CHROME="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"

start "" %CHROME% ^
    --kiosk ^
    --app=https://localhost:8443 ^
    --user-data-dir="%KIOSK_PROFILE%" ^
    --no-restore-last-session ^
    --no-first-run ^
    --no-default-browser-check ^
    --disable-session-crashed-bubble ^
    --disable-infobars ^
    --disable-translate ^
    --disable-features=TranslateUI ^
    --disable-sync ^
    --password-store=basic ^
    --ignore-certificate-errors ^
    --allow-insecure-localhost
