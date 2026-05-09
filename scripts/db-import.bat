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

if not exist "%PROJECT_DIR%\dumps\%DUMP_TAG%" (
  echo ERROR: Dump not found: .\dumps\%DUMP_TAG%
  echo.
  echo Available dumps:
  dir /b "%PROJECT_DIR%\dumps" 2>nul || echo   (none^)
  exit /b 1
)

echo Importing all databases (tag: %DUMP_TAG%)...
echo Source: %PROJECT_DIR%\dumps\%DUMP_TAG%\

cd /d "%PROJECT_DIR%"

set DUMP_TAG=%DUMP_TAG%
docker-compose --profile db-import up --abort-on-container-exit
if errorlevel 1 (
  echo ERROR: Import failed
  exit /b 1
)

docker-compose --profile db-import down --remove-orphans 2>nul

echo.
echo Import complete from .\dumps\%DUMP_TAG%\
