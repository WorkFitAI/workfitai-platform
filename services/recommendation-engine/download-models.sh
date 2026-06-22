#!/bin/bash
# =============================================================================
# Download WorkFitAI models from HuggingFace (private repos)
#
# Works in two modes:
#   Host mode:      reads HF_TOKEN / HF_TOKEN_VP from .env.local (project root)
#   Container mode: reads HF_TOKEN / HF_TOKEN_VP from injected env vars
#
# Usage:
#   ./download-models.sh           # skip if models already exist
#   ./download-models.sh --force   # always remove and re-download
#
# Repos:
#   thanhdi3110/workfitai-jobrecomendation  (HF_TOKEN)    → models/cv-refer/
#   vanphat15it/job-recommendation          (HF_TOKEN_VP) → models/job-recommend/
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

FORCE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/models"

echo -e "${BLUE}🤗 WorkFitAI Model Downloader${NC}"
echo "======================================="
echo -e "Models dir: ${YELLOW}$MODELS_DIR${NC}"

# ---------------------------------------------------------------------------
# Load tokens: prefer env vars already set (container mode), fall back to
# .env.local two levels up (host mode).
# ---------------------------------------------------------------------------
if [ -z "$HF_TOKEN" ] || [ -z "$HF_TOKEN_VP" ]; then
    ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
    ENV_FILE="$ROOT_DIR/.env.local"

    if [ -f "$ENV_FILE" ]; then
        parse_env_var() {
            local key="$1"
            grep -E "^${key}=" "$ENV_FILE" | head -1 | sed -E "s/^${key}=['\"]?//;s/['\"]?\s*$//" | tr -d '\r'
        }
        HF_TOKEN="${HF_TOKEN:-$(parse_env_var HF_TOKEN)}"
        HF_TOKEN_VP="${HF_TOKEN_VP:-$(parse_env_var HF_TOKEN_VP)}"
    fi
fi

if [ -z "$HF_TOKEN" ]; then
    echo -e "${RED}❌ HF_TOKEN not set. Provide via env var or .env.local${NC}"
    exit 1
fi

if [ -z "$HF_TOKEN_VP" ]; then
    echo -e "${RED}❌ HF_TOKEN_VP not set. Provide via env var or .env.local${NC}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Skip download if models already exist (unless --force)
# ---------------------------------------------------------------------------
CV_REFER_EXISTS=false
JOB_RECOMMEND_EXISTS=false

[ -d "$MODELS_DIR/cv-refer" ] && [ "$(ls -A "$MODELS_DIR/cv-refer" 2>/dev/null)" ] && CV_REFER_EXISTS=true
[ -d "$MODELS_DIR/job-recommend" ] && [ "$(ls -A "$MODELS_DIR/job-recommend" 2>/dev/null)" ] && JOB_RECOMMEND_EXISTS=true

if $CV_REFER_EXISTS && $JOB_RECOMMEND_EXISTS && [ "$FORCE" != "--force" ]; then
    echo -e "${GREEN}✅ Models already downloaded, skipping.${NC}"
    echo "   Use '--force' to remove and re-download."
    exit 0
fi

# ---------------------------------------------------------------------------
# Clean model subdirectories (never remove the mount point itself —
# Docker bind-mounts cannot be deleted while the container is running).
# ---------------------------------------------------------------------------
echo -e "${YELLOW}🗑️  Cleaning existing model subdirectories...${NC}"
rm -rf "$MODELS_DIR/cv-refer" "$MODELS_DIR/job-recommend"

echo -e "${YELLOW}📁 Creating model directories...${NC}"
mkdir -p "$MODELS_DIR/cv-refer"
mkdir -p "$MODELS_DIR/job-recommend"

# ---------------------------------------------------------------------------
# Ensure Python + huggingface_hub available
# ---------------------------------------------------------------------------
if ! command -v python3 &>/dev/null; then
    echo -e "${RED}❌ python3 not found. Please install Python 3.9+${NC}"
    exit 1
fi

if [ -d "$SCRIPT_DIR/venv" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/venv/bin/activate"
    echo -e "${GREEN}✅ Using existing venv${NC}"
fi

echo -e "${YELLOW}📦 Checking huggingface_hub...${NC}"
python3 -c "import huggingface_hub" 2>/dev/null || {
    echo -e "${YELLOW}Installing huggingface_hub...${NC}"
    pip3 install -q "huggingface_hub>=0.20.0"
}

export HF_TOKEN
export HF_TOKEN_VP

# ---------------------------------------------------------------------------
# Python download script
# ---------------------------------------------------------------------------
python3 - <<PYEOF
import os
import sys
from pathlib import Path
from huggingface_hub import snapshot_download, HfApi

models_dir = Path("$MODELS_DIR")

repos = [
    {
        "repo_id": "thanhdi3110/workfitai-jobrecomendation",
        "token":   os.environ.get("HF_TOKEN"),
        "label":   "cv-refer",
        "dest":    models_dir / "cv-refer",
    },
    {
        "repo_id": "vanphat15it/job-recommendation",
        "token":   os.environ.get("HF_TOKEN_VP"),
        "label":   "job-recommend",
        "dest":    models_dir / "job-recommend",
    },
]

for repo in repos:
    print(f"\n🔐 Verifying token for {repo['label']} ({repo['repo_id']})...")
    try:
        api = HfApi()
        user = api.whoami(token=repo["token"])
        print(f"  Logged in as: {user['name']}")
    except Exception as e:
        print(f"  ❌ Token error: {e}")
        sys.exit(1)

    dest: Path = repo["dest"]
    dest.mkdir(parents=True, exist_ok=True)
    print(f"\n📥 Downloading {repo['repo_id']} → {dest.relative_to(models_dir.parent)}/")
    try:
        snapshot_download(
            repo_id=repo["repo_id"],
            local_dir=str(dest),
            token=repo["token"],
            ignore_patterns=["*.msgpack", "flax_model*", "tf_model*", "rust_model*"],
            local_dir_use_symlinks=False,
        )
        print(f"  ✅ Done")
    except Exception as e:
        print(f"  ❌ Failed: {e}")
        sys.exit(1)

print("\n✅ All downloads complete!")
print("\nModel structure:")
for d in sorted(models_dir.rglob("*")):
    depth = len(d.relative_to(models_dir).parts)
    if d.is_dir() and depth <= 2 and not d.name.startswith("."):
        indent = "  " * depth
        print(f"{indent}📁 {d.name}/")
PYEOF

echo ""
echo -e "${GREEN}🎉 Models ready!${NC}"