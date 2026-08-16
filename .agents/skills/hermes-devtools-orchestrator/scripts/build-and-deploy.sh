#!/usr/bin/env bash
# Build hermes-pipeline binary and deploy to a target project.
#
# Usage:
#   ./build-and-deploy.sh <target-project-path> [--skip-build] [--init]
#
# Options:
#   --skip-build   Skip build step, use existing binary
#   --init         Run 'hermes-pipeline init' on target after deploy
#
# Examples:
#   ./build-and-deploy.sh /path/to/my-project
#   ./build-and-deploy.sh /path/to/my-project --init
#   ./build-and-deploy.sh /path/to/my-project --skip-build

set -euo pipefail

# ─── Config ─────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SKILL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# hermes_dev root is 4 levels up from scripts/
HERMES_ROOT="$(cd "$SKILL_DIR/../../.." && pwd)"
BINARY_NAME="hermes-pipeline"
BINARY_PATH="$HERMES_ROOT/dist/$BINARY_NAME"
BUILD_SCRIPT="$HERMES_ROOT/build.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[DEPLOY]${NC} $*"; }
ok()   { echo -e "${GREEN}[  OK  ]${NC} $*"; }
warn() { echo -e "${YELLOW}[ WARN ]${NC} $*"; }
err()  { echo -e "${RED}[ERROR ]${NC} $*" >&2; }

# ─── Parse arguments ────────────────────────────────────────
TARGET_PATH=""
SKIP_BUILD=false
DO_INIT=false

for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=true ;;
        --init)       DO_INIT=true ;;
        -*)           err "Unknown option: $arg"; exit 1 ;;
        *)            TARGET_PATH="$arg" ;;
    esac
done

if [[ -z "$TARGET_PATH" ]]; then
    err "Usage: $0 <target-project-path> [--skip-build] [--init]"
    exit 1
fi

# Resolve target path
TARGET_PATH="$(cd "$TARGET_PATH" 2>/dev/null && pwd)" || {
    err "Target path does not exist: $TARGET_PATH"
    exit 1
}

log "Hermes root: $HERMES_ROOT"
log "Target:      $TARGET_PATH"

# ─── Phase 1: Build ────────────────────────────────────────
if [[ "$SKIP_BUILD" == "true" ]]; then
    log "Skipping build (--skip-build)"
    if [[ ! -f "$BINARY_PATH" ]]; then
        err "Binary not found at $BINARY_PATH — cannot skip build"
        exit 1
    fi
else
    log "Building binary..."
    if [[ ! -f "$BUILD_SCRIPT" ]]; then
        err "Build script not found: $BUILD_SCRIPT"
        exit 1
    fi
    cd "$HERMES_ROOT"
    bash "$BUILD_SCRIPT" --test
    cd - > /dev/null
fi

# ─── Phase 2: Verify binary ────────────────────────────────
if [[ ! -f "$BINARY_PATH" ]]; then
    err "Binary not found after build: $BINARY_PATH"
    exit 1
fi

BINARY_SIZE=$(du -sh "$BINARY_PATH" | awk '{print $1}')
ok "Binary ready: $BINARY_PATH ($BINARY_SIZE)"

# ─── Phase 3: Deploy ───────────────────────────────────────
log "Deploying to $TARGET_PATH..."
cp "$BINARY_PATH" "$TARGET_PATH/$BINARY_NAME"
chmod +x "$TARGET_PATH/$BINARY_NAME"

# Verify deployment
if "$TARGET_PATH/$BINARY_NAME" --help > /dev/null 2>&1; then
    ok "Binary deployed and verified"
else
    err "Deployed binary failed verification"
    exit 1
fi

# ─── Phase 4: Init (optional) ──────────────────────────────
if [[ "$DO_INIT" == "true" ]]; then
    log "Initializing config in target..."
    cd "$TARGET_PATH"
    "./$BINARY_NAME" init 2>&1 || warn "Init may have partially failed"
    cd - > /dev/null

    if [[ -d "$TARGET_PATH/.hermes" ]]; then
        ok "Config directory initialized: $TARGET_PATH/.hermes/"
    else
        warn "Config directory not created — manual init may be needed"
    fi
fi

# ─── Summary ───────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════"
echo "  DEPLOY COMPLETE"
echo "════════════════════════════════════════════════════"
echo "  Binary:  $TARGET_PATH/$BINARY_NAME"
echo "  Size:    $BINARY_SIZE"
echo ""
echo "  Next steps:"
echo "    cd $TARGET_PATH"
if [[ "$DO_INIT" != "true" ]]; then
    echo "    ./$BINARY_NAME init                    # Init config"
fi
echo "    # Edit pipeline_config.yml"
echo "    ./$BINARY_NAME start --config pipeline_config.yml"
echo "════════════════════════════════════════════════════"
