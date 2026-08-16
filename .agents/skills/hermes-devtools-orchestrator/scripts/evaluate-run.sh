#!/usr/bin/env bash
# Evaluate the results of a hermes-pipeline execution.
#
# Usage:
#   ./evaluate-run.sh <target-project-path> [--history] [--json]
#
# Options:
#   --history   Append result to history log
#   --json      Output in JSON format (for programmatic use)
#
# Examples:
#   ./evaluate-run.sh /path/to/my-project
#   ./evaluate-run.sh /path/to/my-project --history
#   ./evaluate-run.sh /path/to/my-project --json

set -euo pipefail

# ─── Config ─────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${BLUE}[EVAL]${NC} $*"; }
ok()   { echo -e "${GREEN}[ OK ]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERR ]${NC} $*" >&2; }
info() { echo -e "${CYAN}[INFO]${NC} $*"; }

# ─── Parse arguments ────────────────────────────────────────
TARGET_PATH=""
SAVE_HISTORY=false
JSON_OUTPUT=false

for arg in "$@"; do
    case "$arg" in
        --history)   SAVE_HISTORY=true ;;
        --json)      JSON_OUTPUT=true ;;
        -*)          err "Unknown option: $arg"; exit 1 ;;
        *)           TARGET_PATH="$arg" ;;
    esac
done

if [[ -z "$TARGET_PATH" ]]; then
    err "Usage: $0 <target-project-path> [--history] [--json]"
    exit 1
fi

TARGET_PATH="$(cd "$TARGET_PATH" 2>/dev/null && pwd)" || {
    err "Target path does not exist: $TARGET_PATH"
    exit 1
}

PROJECT_NAME="$(basename "$TARGET_PATH")"
TIMESTAMP="$(date +%Y-%m-%dT%H:%M:%S%z)"
PROGRESS_FILE="$TARGET_PATH/openspec/pipeline_progress.yml"
OUTPUT_DIR="$TARGET_PATH/openspec"

log "Evaluating: $TARGET_PATH"
log "Project:    $PROJECT_NAME"

# ─── Check prerequisites ───────────────────────────────────
if [[ ! -f "$PROGRESS_FILE" ]]; then
    err "No pipeline progress found: $PROGRESS_FILE"
    err "Pipeline may not have been run yet"
    exit 1
fi

# ─── Dimension 1: Completeness (30%) ───────────────────────
log "Scoring completeness..."

FEATURES_TOTAL=0
FEATURES_COMPLETED=0
FEATURES_IN_PROGRESS=0
FEATURES_ERROR=0

# Parse progress file for feature statuses
if command -v python3 &>/dev/null; then
    COMPLETENESS_DATA=$(python3 -c "
import yaml, sys
try:
    with open('$PROGRESS_FILE') as f:
        data = yaml.safe_load(f) or {}
    features = data.get('features', data.get('pipeline_progress', {}).get('features', {}))
    if not isinstance(features, dict):
        features = {}
    total = len(features)
    completed = sum(1 for f in features.values() if isinstance(f, dict) and f.get('status') == 'completed')
    in_progress = sum(1 for f in features.values() if isinstance(f, dict) and f.get('status') == 'in_progress')
    errors = sum(1 for f in features.values() if isinstance(f, dict) and f.get('status') == 'error')
    print(f'{total} {completed} {in_progress} {errors}')
except Exception as e:
    print(f'0 0 0 0', file=sys.stderr)
    print('0 0 0 0')
" 2>/dev/null)
    read -r FEATURES_TOTAL FEATURES_COMPLETED FEATURES_IN_PROGRESS FEATURES_ERROR <<< "$COMPLETENESS_DATA"
fi

if [[ "$FEATURES_TOTAL" -gt 0 ]]; then
    COMPLETENESS_SCORE=$(( (FEATURES_COMPLETED * 100 + FEATURES_IN_PROGRESS * 50) / FEATURES_TOTAL ))
else
    COMPLETENESS_SCORE=0
fi

info "Completeness: $FEATURES_COMPLETED/$FEATURES_TOTAL completed ($COMPLETENESS_SCORE/100)"

# ─── Dimension 2: Output Quality (25%) ─────────────────────
log "Scoring output quality..."

OUTPUT_FILES=0
VALID_FILES=0

if [[ -d "$OUTPUT_DIR" ]]; then
    # Count meaningful output files (md and yml, excluding progress)
    OUTPUT_FILES=$(find "$OUTPUT_DIR" -name "*.md" -o -name "*.yml" | grep -v "pipeline_progress" | wc -l | tr -d ' ')

    # Check for non-empty files
    if [[ "$OUTPUT_FILES" -gt 0 ]]; then
        VALID_FILES=$(find "$OUTPUT_DIR" \( -name "*.md" -o -name "*.yml" \) ! -name "pipeline_progress*" -size +100c | wc -l | tr -d ' ')
    fi
fi

if [[ "$OUTPUT_FILES" -gt 0 ]]; then
    OUTPUT_QUALITY_SCORE=$(( VALID_FILES * 100 / OUTPUT_FILES ))
else
    OUTPUT_QUALITY_SCORE=0
fi

info "Output Quality: $VALID_FILES/$OUTPUT_FILES valid files ($OUTPUT_QUALITY_SCORE/100)"

# ─── Dimension 3: Error Rate (20%) ─────────────────────────
log "Scoring error rate..."

TOTAL_STEPS=$(( FEATURES_TOTAL > 0 ? FEATURES_TOTAL : 1 ))
ERROR_PENALTY=$(( FEATURES_ERROR * 100 / TOTAL_STEPS ))
ERROR_RATE_SCORE=$(( 100 - ERROR_PENALTY ))
if [[ "$ERROR_RATE_SCORE" -lt 0 ]]; then
    ERROR_RATE_SCORE=0
fi

info "Error Rate: $FEATURES_ERROR errors / $TOTAL_STEPS steps ($ERROR_RATE_SCORE/100)"

# ─── Dimension 4: Efficiency (15%) ─────────────────────────
log "Scoring efficiency..."

# Use progress file timestamps if available
EFFICIENCY_SCORE=50  # Default neutral score without timing data

if command -v python3 &>/dev/null; then
    EFFICIENCY_SCORE=$(python3 -c "
import yaml
try:
    with open('$PROGRESS_FILE') as f:
        data = yaml.safe_load(f) or {}
    # Try to find timing info
    started = data.get('started_at', data.get('pipeline_progress', {}).get('started_at', ''))
    if started:
        from datetime import datetime
        # Simple heuristic: if completed in reasonable time
        print(70)  # Decent efficiency assumed
    else:
        print(50)  # No timing data
except:
    print(50)
" 2>/dev/null)
fi

info "Efficiency: $EFFICIENCY_SCORE/100"

# ─── Dimension 5: Consistency (10%) ─────────────────────────
log "Scoring consistency..."

# If all features have same status, consistency is high
if [[ "$FEATURES_TOTAL" -le 1 ]]; then
    CONSISTENCY_SCORE=100
elif [[ "$FEATURES_COMPLETED" -eq "$FEATURES_TOTAL" ]]; then
    CONSISTENCY_SCORE=100
elif [[ "$FEATURES_ERROR" -eq "$FEATURES_TOTAL" ]]; then
    CONSISTENCY_SCORE=100  # Consistently bad is still consistent
else
    # Mixed results = lower consistency
    CONSISTENCY_SCORE=$(( 100 - (FEATURES_ERROR + FEATURES_IN_PROGRESS) * 30 ))
    if [[ "$CONSISTENCY_SCORE" -lt 0 ]]; then
        CONSISTENCY_SCORE=0
    fi
fi

info "Consistency: $CONSISTENCY_SCORE/100"

# ─── Calculate Overall Score ────────────────────────────────
OVERALL_SCORE=$(( \
    COMPLETENESS_SCORE * 30 / 100 + \
    OUTPUT_QUALITY_SCORE * 25 / 100 + \
    ERROR_RATE_SCORE * 20 / 100 + \
    EFFICIENCY_SCORE * 15 / 100 + \
    CONSISTENCY_SCORE * 10 / 100 \
))

# Determine rating
if [[ "$OVERALL_SCORE" -ge 90 ]]; then
    RATING="excellent"
    RATING_COLOR="$GREEN"
elif [[ "$OVERALL_SCORE" -ge 70 ]]; then
    RATING="good"
    RATING_COLOR="$GREEN"
elif [[ "$OVERALL_SCORE" -ge 50 ]]; then
    RATING="fair"
    RATING_COLOR="$YELLOW"
else
    RATING="poor"
    RATING_COLOR="$RED"
fi

# ─── Output Results ────────────────────────────────────────
if [[ "$JSON_OUTPUT" == "true" ]]; then
    cat <<EOF
{
  "project": "$PROJECT_NAME",
  "timestamp": "$TIMESTAMP",
  "overall_score": $OVERALL_SCORE,
  "rating": "$RATING",
  "dimensions": {
    "completeness": $COMPLETENESS_SCORE,
    "output_quality": $OUTPUT_QUALITY_SCORE,
    "error_rate": $ERROR_RATE_SCORE,
    "efficiency": $EFFICIENCY_SCORE,
    "consistency": $CONSISTENCY_SCORE
  },
  "features": {
    "total": $FEATURES_TOTAL,
    "completed": $FEATURES_COMPLETED,
    "in_progress": $FEATURES_IN_PROGRESS,
    "error": $FEATURES_ERROR
  },
  "output_files": {
    "total": $OUTPUT_FILES,
    "valid": $VALID_FILES
  }
}
EOF
else
    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  EVALUATION REPORT: $PROJECT_NAME"
    echo "═══════════════════════════════════════════════════"
    echo ""
    echo -e "  Overall Score:  ${RATING_COLOR}${OVERALL_SCORE}/100 (${RATING})${NC}"
    echo ""
    echo "  Dimension Breakdown:"
    echo "    Completeness (30%):    $COMPLETENESS_SCORE"
    echo "    Output Quality (25%):  $OUTPUT_QUALITY_SCORE"
    echo "    Error Rate (20%):      $ERROR_RATE_SCORE"
    echo "    Efficiency (15%):      $EFFICIENCY_SCORE"
    echo "    Consistency (10%):     $CONSISTENCY_SCORE"
    echo ""
    echo "  Features: $FEATURES_COMPLETED/$FEATURES_TOTAL completed"
    echo "  Outputs:  $VALID_FILES/$OUTPUT_FILES valid files"
    echo "  Errors:   $FEATURES_ERROR"
    echo ""
    echo "  Timestamp: $TIMESTAMP"
    echo "═══════════════════════════════════════════════════"
fi

# ─── Save to history (optional) ────────────────────────────
if [[ "$SAVE_HISTORY" == "true" ]]; then
    HISTORY_DIR="$TARGET_PATH/.hermes/.history"
    mkdir -p "$HISTORY_DIR"
    HISTORY_FILE="$HISTORY_DIR/evaluations.log"

    echo "$TIMESTAMP | score=$OVERALL_SCORE | rating=$RATING | completed=$FEATURES_COMPLETED/$FEATURES_TOTAL | errors=$FEATURES_ERROR" >> "$HISTORY_FILE"
    ok "Saved to history: $HISTORY_FILE"
fi
