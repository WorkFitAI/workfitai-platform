@echo off
setlocal enabledelayedexpansion

set SERVICES=monitoring-service notification-service cv-service job-service user-service application-service auth-service api-gateway recommendation-engine
set FLAGS=-Dspring.cloud.vault.enabled=false -Dspring.cloud.consul.enabled=false
set REPORT_SCRIPT=scripts\generate-test-report.py
set REPORT_FILE=test-report.html

if not "%~1"=="" goto :run_one

:: ── Run all services ──────────────────────────────────────────────────────────
set PASSED=
set FAILED=

for %%S in (%SERVICES%) do (
    echo.
    echo [INFO] ====================================================
    echo [INFO] Testing %%S
    echo [INFO] ====================================================
    if "%%S"=="recommendation-engine" (
        call :run_python_svc
    ) else (
        call services\%%S\mvnw.cmd -f services/%%S/pom.xml test %FLAGS%
    )
    if !ERRORLEVEL! equ 0 (
        set PASSED=!PASSED! %%S
    ) else (
        set FAILED=!FAILED! %%S
    )
)

echo.
echo [INFO] ====================================================
echo [INFO] RESULTS
echo [INFO] ====================================================
if not "!PASSED!"=="" echo [PASS]!PASSED!
if not "!FAILED!"=="" echo [FAIL]!FAILED!

call :generate_report

if not "!FAILED!"=="" (
    exit /b 1
)
exit /b 0

:: ── Run one service ───────────────────────────────────────────────────────────
:run_one
echo [INFO] Testing %~1
if "%~1"=="recommendation-engine" (
    call :run_python_svc
) else (
    call services\%~1\mvnw.cmd -f services/%~1/pom.xml test %FLAGS%
)
set SVC_CODE=%ERRORLEVEL%
call :generate_report
exit /b %SVC_CODE%

:: ── Run recommendation-engine Python tests ────────────────────────────────────
:run_python_svc
set PY_SVC=services\recommendation-engine
set PY_EXE=%PY_SVC%\venv\Scripts\python.exe

if not exist "%PY_EXE%" (
    echo [INFO] venv not found -- initializing...
    where python >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        python -m venv "%PY_SVC%\venv"
    ) else (
        where python3 >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            python3 -m venv "%PY_SVC%\venv"
        ) else (
            echo [ERROR] python not found -- cannot initialize venv for recommendation-engine
            exit /b 1
        )
    )
    echo [INFO] venv initialized
)

:: Re-sync every run (not just on first creation) -- otherwise an existing venv
:: silently drifts from requirements.txt as dependencies are added/pinned later,
:: e.g. the httpx pin only came in via ollama==0.3.3 and a pre-existing venv would
:: never pick it up, leaving an incompatible httpx that breaks starlette's TestClient.
call "%PY_SVC%\venv\Scripts\pip.exe" install --quiet -r "%PY_SVC%\requirements.txt"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Failed to install dependencies for recommendation-engine
    exit /b 1
)

mkdir "%PY_SVC%\target\surefire-reports" 2>nul
mkdir "%PY_SVC%\target\site" 2>nul

pushd "%PY_SVC%"
call venv\Scripts\python.exe -m pytest
set PY_EXIT=!ERRORLEVEL!
popd
exit /b %PY_EXIT%

:: ── Generate HTML report ─────────────────────────────────────────────────────
:generate_report
echo.
echo [REPORT] Generating HTML report...

where python >nul 2>&1
if %ERRORLEVEL% equ 0 (
    python %REPORT_SCRIPT% --root "." --output "%REPORT_FILE%"
    goto :open_report
)
where python3 >nul 2>&1
if %ERRORLEVEL% equ 0 (
    python3 %REPORT_SCRIPT% --root "." --output "%REPORT_FILE%"
    goto :open_report
)
echo [WARN] Python not found -- skipping HTML report generation
goto :eof

:open_report
if exist "%REPORT_FILE%" (
    echo [REPORT] Opening: %CD%\%REPORT_FILE%
    start "" "%CD%\%REPORT_FILE%"
)
goto :eof
