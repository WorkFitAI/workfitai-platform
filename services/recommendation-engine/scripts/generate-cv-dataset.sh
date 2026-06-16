#!/usr/bin/env bash
# =============================================================================
# generate-cv-dataset.sh
# =============================================================================
# Sinh CV PDF cho WorkFitAI. Hỗ trợ 4 modes:
#
#   generate  (mặc định)  Sinh fake CVs dùng Faker (đa dạng ngành nghề)
#   job                   Sinh CVs biased theo job spec – dùng cho integration test
#   csv                   Sinh CVs từ dữ liệu thật trong file .csv
#   extract               Extract 4 model fields từ các PDF đã có
#
# USAGE
# -----
#   bash generate-cv-dataset.sh [COUNT] [OUTPUT_DIR] [SEED]
#   bash generate-cv-dataset.sh job --title "..." [--skills "..."] [--level junior|mid|senior] [--count N] [--output DIR]
#   bash generate-cv-dataset.sh csv <CSV_FILE> [ROW_INDEX] [OUTPUT_DIR]
#   bash generate-cv-dataset.sh extract <PDF_GLOB_OR_FILE...> [--verbose]
#
# EXAMPLES
#   bash generate-cv-dataset.sh 100
#   bash generate-cv-dataset.sh job --title "Senior Backend Engineer" --skills "Java,Spring Boot,Docker" --level senior --count 50
#   bash generate-cv-dataset.sh job --title "Product Manager" --count 30
#   bash generate-cv-dataset.sh csv scripts/output/cvs/test.csv
#   bash generate-cv-dataset.sh csv scripts/output/cvs/test.csv 42
#   bash generate-cv-dataset.sh extract "scripts/output/cvs/*.pdf"
# =============================================================================
# REQUIREMENTS
#   Python 3.9+  (kiểm tra bằng: python3 --version)
#   pip packages được cài tự động vào venv tạm thời (không ảnh hưởng global)
# =============================================================================

set -euo pipefail

# ── Màu sắc terminal ──────────────────────────────────────────────────────────
BOLD=$'\e[1m'
GREEN=$'\e[32m'
BLUE=$'\e[34m'
YELLOW=$'\e[33m'
RED=$'\e[31m'
CYAN=$'\e[36m'
RESET=$'\e[0m'

log_info()    { echo "${BLUE}[INFO]${RESET} $*"; }
log_success() { echo "${GREEN}[OK]${RESET}   $*"; }
log_warn()    { echo "${YELLOW}[WARN]${RESET} $*"; }
log_error()   { echo "${RED}[ERR]${RESET}  $*" >&2; }
log_header()  { echo; echo "${BOLD}${CYAN}═══ $* ═══${RESET}"; echo; }

# ── Tham số ───────────────────────────────────────────────────────────────────
MODE="generate"           # generate | job | csv | extract
EXTRACT_PDFS=()
EXTRACT_VERBOSE=""
CSV_FILE=""
CSV_ROW=""
JOB_JSON=""               # path to job spec JSON (job mode, optional)
JOB_TITLE=""
JOB_SKILLS=""
JOB_LEVEL=""
JOB_COUNT=""
JOB_MIX_RATE=""
OUTPUT_DIR=""

# Detect mode from first argument
if [[ "${1:-}" == "extract" ]]; then
    MODE="extract"
    shift
    for arg in "$@"; do
        if [[ "${arg}" == "--verbose" ]]; then
            EXTRACT_VERBOSE="--verbose"
        else
            EXTRACT_PDFS+=("${arg}")
        fi
    done
elif [[ "${1:-}" == "job" ]]; then
    MODE="job"
    shift
    # Parse named args: --title, --skills, --level, --count, --output, --mix-rate
    # Or legacy: job <JSON_FILE> [COUNT] [OUTPUT_DIR]
    while [[ $# -gt 0 ]]; do
        case "${1}" in
            --title)     JOB_TITLE="${2:-}";    shift 2 ;;
            --skills)    JOB_SKILLS="${2:-}";   shift 2 ;;
            --level)     JOB_LEVEL="${2:-}";    shift 2 ;;
            --count)     JOB_COUNT="${2:-}";    shift 2 ;;
            --output)    OUTPUT_DIR="${2:-}";   shift 2 ;;
            --mix-rate)  JOB_MIX_RATE="${2:-}"; shift 2 ;;
            --*)         shift 2 ;;  # unknown flag, skip
            *)
                # Positional: legacy JSON file or count
                if [[ "${1}" =~ \.json$ ]]; then
                    JOB_JSON="${1}"; shift
                elif [[ "${1}" =~ ^[0-9]+$ ]] && [[ -z "${JOB_COUNT}" ]]; then
                    JOB_COUNT="${1}"; shift
                elif [[ -z "${OUTPUT_DIR}" ]]; then
                    OUTPUT_DIR="${1}"; shift
                else
                    shift
                fi
                ;;
        esac
    done
elif [[ "${1:-}" == "csv" ]]; then
    MODE="csv"
    shift
    CSV_FILE="${1:-}"
    if [[ -z "${CSV_FILE}" ]]; then
        echo "${RED}[ERR]${RESET}  csv mode requires a CSV file path." >&2
        echo "  Usage: bash generate-cv-dataset.sh csv <CSV_FILE> [ROW_INDEX] [OUTPUT_DIR]" >&2
        exit 1
    fi
    shift || true
    if [[ "${1:-}" =~ ^[0-9]+$ ]]; then
        CSV_ROW="${1}"
        shift || true
    fi
    OUTPUT_DIR="${1:-}"
    shift || true
else
    COUNT="${1:-20}"
    OUTPUT_DIR="${2:-}"
    SEED="${3:-42}"
fi

# Resolve đường dẫn tương đối so với script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/generate_cv_pdfs.py"

# Mặc định output ngay cạnh scripts/ (cho generate và csv mode; job mode tự tính sau)
if [[ "${MODE}" == "generate" || "${MODE}" == "csv" ]] && [[ -z "${OUTPUT_DIR}" ]]; then
    OUTPUT_DIR="${SCRIPT_DIR}/output/cvs"
fi

# Venv được đặt trong scripts/ để không ảnh hưởng project chính
VENV_DIR="${SCRIPT_DIR}/.cv_gen_venv"

# ── Kiểm tra prerequisites ────────────────────────────────────────────────────
log_header "WorkFitAI · CV Dataset Generator"

# Kiểm tra Python
if ! command -v python3 &>/dev/null; then
    log_error "python3 không tìm thấy. Hãy cài Python 3.9+."
    exit 1
fi

PY_VERSION=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
PY_MAJOR=$(python3 -c "import sys; print(sys.version_info.major)")
PY_MINOR=$(python3 -c "import sys; print(sys.version_info.minor)")

if [[ "${PY_MAJOR}" -lt 3 ]] || { [[ "${PY_MAJOR}" -eq 3 ]] && [[ "${PY_MINOR}" -lt 9 ]]; }; then
    log_error "Cần Python >= 3.9, hiện có ${PY_VERSION}."
    exit 1
fi

log_info "Python ${PY_VERSION} ✓"

# Kiểm tra script tồn tại
if [[ ! -f "${PYTHON_SCRIPT}" ]]; then
    log_error "Không tìm thấy ${PYTHON_SCRIPT}"
    exit 1
fi

# ── Cài đặt venv + dependencies ───────────────────────────────────────────────
log_header "Kiểm tra / Cài đặt dependencies"

if [[ ! -d "${VENV_DIR}" ]]; then
    log_info "Tạo virtual environment tại ${VENV_DIR} ..."
    python3 -m venv "${VENV_DIR}"
fi

# Activate venv
# shellcheck disable=SC1091
source "${VENV_DIR}/bin/activate"

REQUIRED_PACKAGES=(
    "reportlab>=4.0.0"
    "Pillow>=10.0.0"
)

# faker chỉ cần cho generate và job mode
if [[ "${MODE}" == "generate" || "${MODE}" == "job" ]]; then
    REQUIRED_PACKAGES+=("faker>=20.0.0")
fi

# extract mode cần thêm pdfplumber
if [[ "${MODE}" == "extract" ]]; then
    REQUIRED_PACKAGES+=("pdfplumber>=0.10.0")
fi

MISSING=()
for pkg in "${REQUIRED_PACKAGES[@]}"; do
    pkg_name="${pkg%%[><=!]*}"
    if ! python3 -c "import ${pkg_name//-/_}" 2>/dev/null; then
        MISSING+=("${pkg}")
    fi
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
    log_info "Cài đặt: ${MISSING[*]}"
    pip install --quiet --upgrade "${MISSING[@]}"
else
    log_success "Tất cả dependencies đã được cài đặt."
fi

# Verify sau khi cài
VERIFY_MODS=("reportlab" "PIL")
if [[ "${MODE}" == "generate" || "${MODE}" == "job" ]]; then VERIFY_MODS+=("faker"); fi
if [[ "${MODE}" == "extract"  ]]; then VERIFY_MODS+=("pdfplumber"); fi

for check in "${VERIFY_MODS[@]}"; do
    if ! python3 -c "import ${check}" 2>/dev/null; then
        log_error "Không import được module '${check}'. Thử: pip install reportlab Pillow faker pdfplumber"
        deactivate 2>/dev/null || true
        exit 1
    fi
done

log_success "Dependencies OK"

# ── Chạy theo mode ────────────────────────────────────────────────────────────
if [[ "${MODE}" == "job" ]]; then
    # JOB mode — sinh CVs biased theo job
    log_header "Sinh CV PDF(s) cho Job"

    # Validate: cần ít nhất --title hoặc JSON file
    if [[ -z "${JOB_TITLE}" && -z "${JOB_JSON}" ]]; then
        log_error "job mode cần --title \"...\" hoặc JSON file."
        log_error "  Ví dụ: bash generate-cv-dataset.sh job --title \"Backend Engineer\" --skills \"Java,Spring Boot\" --count 50"
        deactivate 2>/dev/null || true
        exit 1
    fi

    # Resolve JSON file nếu có
    if [[ -n "${JOB_JSON}" && ! -f "${JOB_JSON}" ]]; then
        if [[ -f "${SCRIPT_DIR}/${JOB_JSON}" ]]; then
            JOB_JSON="${SCRIPT_DIR}/${JOB_JSON}"
        else
            log_error "Không tìm thấy job spec: ${JOB_JSON}"
            deactivate 2>/dev/null || true
            exit 1
        fi
    fi

    # Nếu không chỉ định output, dùng output/cvs/job_<title_slug>
    if [[ -z "${OUTPUT_DIR}" ]]; then
        if [[ -n "${JOB_TITLE}" ]]; then
            TITLE_SLUG=$(echo "${JOB_TITLE}" | tr '[:upper:]' '[:lower:]' | tr ' ' '_' | tr -cd '[:alnum:]_')
        else
            TITLE_SLUG=$(basename "${JOB_JSON}" .json)
        fi
        OUTPUT_DIR="${SCRIPT_DIR}/output/cvs/${TITLE_SLUG}"
    fi
    mkdir -p "${OUTPUT_DIR}"

    # Build args
    JOB_ARGS=(--output "${OUTPUT_DIR}")
    [[ -n "${JOB_JSON}" ]]      && JOB_ARGS+=(--job-json "${JOB_JSON}")
    [[ -n "${JOB_TITLE}" ]]     && JOB_ARGS+=(--job-title "${JOB_TITLE}")
    [[ -n "${JOB_SKILLS}" ]]    && JOB_ARGS+=(--job-skills "${JOB_SKILLS}")
    [[ -n "${JOB_LEVEL}" ]]     && JOB_ARGS+=(--job-level "${JOB_LEVEL}")
    [[ -n "${JOB_MIX_RATE}" ]]  && JOB_ARGS+=(--job-mix-rate "${JOB_MIX_RATE}")
    [[ -n "${JOB_COUNT}" ]]     && JOB_ARGS+=(--count "${JOB_COUNT}")

    [[ -n "${JOB_TITLE}" ]]  && log_info "Title    -> ${JOB_TITLE}"
    [[ -n "${JOB_JSON}" ]]   && log_info "JSON     -> ${JOB_JSON}"
    [[ -n "${JOB_SKILLS}" ]] && log_info "Skills   -> ${JOB_SKILLS}"
    [[ -n "${JOB_LEVEL}" ]]  && log_info "Level    -> ${JOB_LEVEL}"
    log_info "Output   -> ${OUTPUT_DIR}"
    [[ -n "${JOB_COUNT}" ]] && log_info "Count    -> ${JOB_COUNT}" || log_info "Count    -> 100 (default)"
    echo

    START_TIME=$(date +%s)
    python3 "${PYTHON_SCRIPT}" "${JOB_ARGS[@]}"
    EXIT_CODE=$?

elif [[ "${MODE}" == "csv" ]]; then
    # CSV mode — sinh PDF từ dữ liệu thật trong CSV
    log_header "Sinh CV PDF(s) từ CSV"

    if [[ ! -f "${CSV_FILE}" ]]; then
        if [[ -f "${SCRIPT_DIR}/${CSV_FILE}" ]]; then
            CSV_FILE="${SCRIPT_DIR}/${CSV_FILE}"
        else
            log_error "Không tìm thấy CSV file: ${CSV_FILE}"
            deactivate 2>/dev/null || true
            exit 1
        fi
    fi

    mkdir -p "${OUTPUT_DIR}"
    log_info "CSV    -> ${CSV_FILE}"
    log_info "Output -> ${OUTPUT_DIR}"
    [[ -n "${CSV_ROW}" ]] && log_info "Row    -> ${CSV_ROW} (single row mode)"
    echo

    CSV_ARGS=(--csv "${CSV_FILE}" --output "${OUTPUT_DIR}")
    [[ -n "${CSV_ROW}" ]] && CSV_ARGS+=(--csv-row "${CSV_ROW}")

    START_TIME=$(date +%s)
    python3 "${PYTHON_SCRIPT}" "${CSV_ARGS[@]}"
    EXIT_CODE=$?

elif [[ "${MODE}" == "extract" ]]; then
    # EXTRACT mode
    log_header "Extract fields từ PDF(s)"

    if [[ ${#EXTRACT_PDFS[@]} -eq 0 ]]; then
        log_error "Không có PDF nào được chỉ định. Ví dụ:"
        log_error "  bash generate-cv-dataset.sh extract \"scripts/output/cvs/*.pdf\""
        deactivate 2>/dev/null || true
        exit 1
    fi

    echo "  ${BOLD}PDF sources:${RESET}"
    for f in "${EXTRACT_PDFS[@]}"; do echo "    ${CYAN}${f}${RESET}"; done
    echo

    START_TIME=$(date +%s)
    python3 "${PYTHON_SCRIPT}" --extract "${EXTRACT_PDFS[@]}" ${EXTRACT_VERBOSE}
    EXIT_CODE=$?

else
    # GENERATE mode
    mkdir -p "${OUTPUT_DIR}"
    log_info "Output -> ${OUTPUT_DIR}"

    log_header "Sinh ${COUNT} CV PDF(s)"
    echo "  ${BOLD}Tham so:${RESET}"
    echo "    Count  : ${CYAN}${COUNT}${RESET}"
    echo "    Output : ${CYAN}${OUTPUT_DIR}${RESET}"
    echo "    Seed   : ${CYAN}${SEED}${RESET}"
    echo

    START_TIME=$(date +%s)
    python3 "${PYTHON_SCRIPT}" \
        --count  "${COUNT}" \
        --output "${OUTPUT_DIR}" \
        --seed   "${SEED}"
    EXIT_CODE=$?
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

deactivate 2>/dev/null || true

# ── Kết quả ───────────────────────────────────────────────────────────────────
if [[ ${EXIT_CODE} -ne 0 ]]; then
    log_error "Generator thoát với code ${EXIT_CODE}."
    exit ${EXIT_CODE}
fi

log_header "Hoan thanh"

if [[ "${MODE}" == "generate" || "${MODE}" == "csv" || "${MODE}" == "job" ]]; then
    PDF_COUNT=$(find "${OUTPUT_DIR}" -maxdepth 1 -name "cv_*.pdf" 2>/dev/null | wc -l | tr -d ' ')
    JSON_COUNT=$(find "${OUTPUT_DIR}" -maxdepth 1 -name "cv_*.json" 2>/dev/null | wc -l | tr -d ' ')
    MANIFEST="${OUTPUT_DIR}/manifest.json"

    echo "  ${BOLD}Thong ke:${RESET}"
    echo "    PDF files   : ${GREEN}${PDF_COUNT}${RESET}"
    echo "    JSON files  : ${GREEN}${JSON_COUNT}${RESET}"
    echo "    Manifest    : ${CYAN}${MANIFEST}${RESET}"
    echo "    Thoi gian   : ${ELAPSED}s"
    echo
    echo "  ${BOLD}Cach dung voi cv-refer API:${RESET}"
    echo "  +-------------------------------------------------------------+"
    echo "  |  Lay du lieu 1 CV:                                          |"
    echo "  |    cat ${OUTPUT_DIR}/cv_0001_*.json                         |"
    echo "  |                                                             |"
    echo "  |  Xem manifest:                                              |"
    echo "  |    cat ${OUTPUT_DIR}/manifest.json                          |"
    echo "  |                                                             |"
    echo "  |  Dat cac truong JSON vao CvRankRequest.resumes[]:           |"
    echo "  |    resume_summary, resume_experience,                       |"
    echo "  |    resume_skills,  resume_education                         |"
    echo "  +-------------------------------------------------------------+"
    echo

    # Hien sample json neu it CVs
    SHOW_SAMPLE_LIMIT="${COUNT:-1}"
    if [[ "${MODE}" == "csv" && -n "${CSV_ROW}" ]]; then SHOW_SAMPLE_LIMIT=1; fi
    if [[ ${SHOW_SAMPLE_LIMIT} -le 5 ]]; then
        FIRST_JSON=$(find "${OUTPUT_DIR}" -maxdepth 1 -name "cv_0000_*.json" 2>/dev/null | head -1)
        if [[ -n "${FIRST_JSON}" ]]; then
            echo "  ${BOLD}Sample (cv_0000):${RESET}"
            cat "${FIRST_JSON}" | python3 -m json.tool 2>/dev/null | head -30 || cat "${FIRST_JSON}" | head -30
            echo
        fi
    fi
fi
