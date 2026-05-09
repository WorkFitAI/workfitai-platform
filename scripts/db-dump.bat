@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..

if "%~1"=="" (
  if "%DUMP_TAG%"=="" (
    set DUMP_TAG=latest
  )
) else (
  set DUMP_TAG=%~1
)

echo Dumping all databases (tag: %DUMP_TAG%)...
echo Output: %PROJECT_DIR%\dumps\%DUMP_TAG%\

cd /d "%PROJECT_DIR%"

set DUMP_TAG=%DUMP_TAG%
docker-compose --profile db-dump up --abort-on-container-exit
if errorlevel 1 (
  echo ERROR: Dump failed
  exit /b 1
)

docker-compose --profile db-dump down --remove-orphans 2>nul

echo.
echo All dumps saved to .\dumps\%DUMP_TAG%\
echo   PostgreSQL: .\dumps\%DUMP_TAG%\postgres\
echo   MongoDB:    .\dumps\%DUMP_TAG%\mongo\
