#!/usr/bin/env bash
set -uo pipefail

SERVICES=(
  monitoring-service
  notification-service
  cv-service
  job-service
  user-service
  application-service
  auth-service
  api-gateway
  recommendation-engine
)
FLAGS="-Dspring.cloud.vault.enabled=false -Dspring.cloud.consul.enabled=false"
REPORT_SCRIPT="scripts/generate-test-report.py"
REPORT_FILE="test-report.html"

run_service() {
  local svc="$1"
  echo ""
  echo "[INFO] ===================================================="
  echo "[INFO] Testing ${svc}"
  echo "[INFO] ===================================================="
  "./services/${svc}/mvnw" -f "services/${svc}/pom.xml" test $FLAGS
}

run_recommendation_engine() {
  echo ""
  echo "[INFO] ===================================================="
  echo "[INFO] Testing recommendation-engine"
  echo "[INFO] ===================================================="
  local svc_dir="services/recommendation-engine"

  if [[ ! -x "${svc_dir}/venv/bin/python" ]]; then
    echo "[INFO] venv not found — initializing..."
    local py
    if command -v python3 &>/dev/null; then
      py=python3
    elif command -v python &>/dev/null; then
      py=python
    else
      echo "[ERROR] python3 not found — cannot initialize venv"
      return 1
    fi
    "$py" -m venv "${svc_dir}/venv" || {
      echo "[ERROR] Failed to initialize venv for recommendation-engine"
      return 1
    }
    echo "[INFO] venv initialized"
  fi

  # Re-sync every run (not just on first creation) — otherwise an existing venv
  # silently drifts from requirements.txt as dependencies are added/pinned later,
  # e.g. the httpx pin only came in via ollama==0.3.3 and a pre-existing venv would
  # never pick it up, leaving an incompatible httpx that breaks starlette's TestClient.
  "${svc_dir}/venv/bin/pip" install --quiet -r "${svc_dir}/requirements.txt" || {
    echo "[ERROR] Failed to install dependencies for recommendation-engine"
    return 1
  }

  (cd "$svc_dir" && mkdir -p target/surefire-reports target/site && venv/bin/python -m pytest)
}

run_any_service() {
  local svc="$1"
  if [[ "$svc" == "recommendation-engine" ]]; then
    run_recommendation_engine
  else
    run_service "$svc"
  fi
}

generate_report() {
  local duration="$1"
  if command -v python3 &>/dev/null; then
    PYTHON=python3
  elif command -v python &>/dev/null; then
    PYTHON=python
  else
    echo "[WARN] Python not found — skipping HTML report generation"
    return
  fi

  echo ""
  echo "[REPORT] Generating HTML report..."
  $PYTHON "$REPORT_SCRIPT" --root "." --output "$REPORT_FILE" --duration "$duration"

  # Open in browser if possible
  local abs_path
  abs_path="$(pwd)/${REPORT_FILE}"
  if command -v xdg-open &>/dev/null; then
    xdg-open "$abs_path" &>/dev/null &
  elif command -v open &>/dev/null; then
    open "$abs_path"
  elif command -v start &>/dev/null; then
    start "$abs_path"
  else
    echo "[REPORT] Open manually: file://${abs_path}"
  fi
}

# ── Run one service ────────────────────────────────────────────────────────────
if [[ "${1:-}" != "" ]]; then
  START=$(date +%s)
  run_any_service "$1"
  CODE=$?
  END=$(date +%s)
  generate_report $((END - START))
  exit $CODE
fi

# ── Run all services ───────────────────────────────────────────────────────────
START=$(date +%s)
passed=()
failed=()

for svc in "${SERVICES[@]}"; do
  if run_any_service "$svc"; then
    passed+=("$svc")
  else
    failed+=("$svc")
  fi
done

END=$(date +%s)
DURATION=$((END - START))

echo ""
echo "[INFO] ===================================================="
echo "[INFO] RESULTS"
echo "[INFO] ===================================================="
[[ ${#passed[@]} -gt 0 ]] && echo "[PASS] ${passed[*]}"
if [[ ${#failed[@]} -gt 0 ]]; then
  echo "[FAIL] ${failed[*]}"
fi

generate_report "$DURATION"

if [[ ${#failed[@]} -gt 0 ]]; then
  exit 1
fi
exit 0
