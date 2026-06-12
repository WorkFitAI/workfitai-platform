#!/bin/bash
# =============================================================================
# Download WorkFitAI models from HuggingFace (private repo)
# Uses huggingface_hub Python package (no git-lfs needed)
#
# Usage:
#   ./download-models.sh <HF_TOKEN>
#   HF_TOKEN=hf_xxx ./download-models.sh
#
# HuggingFace repo: thanhdi3110/workfitai-jobrecomendation
# Structure:
#   bi-encoder/      → models/job-recommend/bi-encoder-e5-large/
#                    → models/cv-refer/bi-encoder/         (same model)
#   cross-encoder/   → models/job-recommend/cross-encoder/
#                    → models/cv-refer/cross-encoder/      (same model)
#   explanation-t5/  → models/cv-refer/explanation-t5/
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

HF_TOKEN="${1:-$HF_TOKEN}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/models"
REPO="thanhdi3110/workfitai-jobrecomendation"

echo -e "${BLUE}🤗 WorkFitAI Model Downloader${NC}"
echo "======================================="
echo -e "Repo:       ${YELLOW}$REPO${NC}"
echo -e "Models dir: ${YELLOW}$MODELS_DIR${NC}"
echo ""

if [ -z "$HF_TOKEN" ]; then
    echo -e "${RED}❌ HuggingFace token required!${NC}"
    echo ""
    echo "Usage:  ./download-models.sh hf_xxxxxxxxxxxx"
    echo "Or:     export HF_TOKEN=hf_xxxx && ./download-models.sh"
    echo ""
    echo "Get token: https://huggingface.co/settings/tokens"
    echo "  → New token → Read access → copy"
    exit 1
fi

# ---------------------------------------------------------------------------
# Ensure Python + huggingface_hub available
# ---------------------------------------------------------------------------
if ! command -v python3 &>/dev/null; then
    echo -e "${RED}❌ python3 not found. Please install Python 3.9+${NC}"
    exit 1
fi

# Use venv if exists, else system python
if [ -d "$SCRIPT_DIR/venv" ]; then
    source "$SCRIPT_DIR/venv/bin/activate"
    echo -e "${GREEN}✅ Using existing venv${NC}"
fi

echo -e "${YELLOW}📦 Checking huggingface_hub...${NC}"
python3 -c "import huggingface_hub" 2>/dev/null || {
    echo -e "${YELLOW}Installing huggingface_hub...${NC}"
    pip3 install -q "huggingface_hub>=0.20.0"
}

# ---------------------------------------------------------------------------
# Python download script
# ---------------------------------------------------------------------------
python3 - <<PYEOF
import os
import sys
from pathlib import Path
from huggingface_hub import snapshot_download, HfApi

token = "$HF_TOKEN"
repo_id = "$REPO"
models_dir = Path("$MODELS_DIR")

print("\\n🔐 Verifying token...")
try:
    api = HfApi()
    user = api.whoami(token=token)
    print(f"  Logged in as: {user['name']}")
except Exception as e:
    print(f"  ❌ Token error: {e}")
    sys.exit(1)

def download_subfolder(subfolder: str, local_dir: Path):
    print(f"\\n📥 Downloading {subfolder}/ → {local_dir.relative_to(models_dir.parent)}/")
    local_dir.mkdir(parents=True, exist_ok=True)
    try:
        snapshot_download(
            repo_id=repo_id,
            allow_patterns=[f"{subfolder}/*"],
            local_dir=str(local_dir),
            token=token,
            ignore_patterns=["*.msgpack", "flax_model*", "tf_model*", "rust_model*"],
            local_dir_use_symlinks=False,
        )
        print(f"  ✅ Done")
    except Exception as e:
        print(f"  ❌ Failed: {e}")
        sys.exit(1)

# Download each subfolder
folders = [
    # (hf_subfolder, local_target)
    ("bi-encoder",     models_dir / "job-recommend" / "bi-encoder-e5-large"),
    ("cross-encoder",  models_dir / "job-recommend" / "cross-encoder"),
    ("bi-encoder",     models_dir / "cv-refer" / "bi-encoder"),
    ("cross-encoder",  models_dir / "cv-refer" / "cross-encoder"),
    ("explanation-t5", models_dir / "cv-refer" / "explanation-t5"),
]

for subfolder, target in folders:
    if target.exists() and any(target.iterdir()):
        print(f"\\n⏩ {target.name} already exists, skipping (delete to re-download)")
        continue
    download_subfolder(subfolder, target)

print("\\n✅ All downloads complete!")
print("\\nModel structure:")
for d in sorted(models_dir.rglob("*")):
    depth = len(d.relative_to(models_dir).parts)
    if d.is_dir() and depth <= 2 and not d.name.startswith("."):
        indent = "  " * depth
        print(f"{indent}📁 {d.name}/")
PYEOF

echo ""
echo -e "${GREEN}🎉 Models ready!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  Docker mode:  cd ../../ && ./dev.sh full up recommendation-engine"
echo "  Local mode:   ./run-local.sh"
