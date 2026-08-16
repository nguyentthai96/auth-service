#!/usr/bin/env bash
# =====================================================
# Hermes Pipeline Monitor — Parse and report pipeline logs
# =====================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
PIPELINE_LOG="$LOG_DIR/pipeline.log"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

if [ ! -f "$PIPELINE_LOG" ]; then
    echo -e "${YELLOW}No pipeline log found at $PIPELINE_LOG${NC}"
    echo "Run hermes_dev.sh first to generate logs."
    exit 0
fi

echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════╗"
echo "║        HERMES PIPELINE MONITOR               ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

# =====================================================
# Error Summary
# =====================================================
echo -e "\n${RED}━━━ ERRORS ━━━${NC}"
error_count=$(grep -c '\[FAIL\]' "$PIPELINE_LOG" 2>/dev/null || echo 0)
if [ "$error_count" -gt 0 ]; then
    grep '\[FAIL\]' "$PIPELINE_LOG" | while IFS= read -r line; do
        echo -e "${RED}  ❌ $line${NC}"
    done
    echo -e "\n  Total errors: $error_count"
else
    echo -e "${GREEN}  No errors found ✅${NC}"
fi

# =====================================================
# Warning Summary
# =====================================================
echo -e "\n${YELLOW}━━━ WARNINGS ━━━${NC}"
warn_count=$(grep -c '\[WARN\]' "$PIPELINE_LOG" 2>/dev/null || echo 0)
if [ "$warn_count" -gt 0 ]; then
    grep '\[WARN\]' "$PIPELINE_LOG" | while IFS= read -r line; do
        echo -e "${YELLOW}  ⚠️  $line${NC}"
    done
    echo -e "\n  Total warnings: $warn_count"
else
    echo -e "${GREEN}  No warnings ✅${NC}"
fi

# =====================================================
# Step Summary
# =====================================================
echo -e "\n${BLUE}━━━ STEP RESULTS ━━━${NC}"
grep '\[STEP\]\|\[OK\]\|\[FAIL\]' "$PIPELINE_LOG" | tail -20 | while IFS= read -r line; do
    if echo "$line" | grep -q '\[OK\]'; then
        echo -e "${GREEN}  $line${NC}"
    elif echo "$line" | grep -q '\[FAIL\]'; then
        echo -e "${RED}  $line${NC}"
    else
        echo -e "${BLUE}  $line${NC}"
    fi
done

# =====================================================
# Timing
# =====================================================
echo -e "\n${BLUE}━━━ TIMING ━━━${NC}"
first_time=$(head -1 "$PIPELINE_LOG" | grep -oP '\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\]' || echo "N/A")
last_time=$(tail -1 "$PIPELINE_LOG" | grep -oP '\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\]' || echo "N/A")
echo "  Start: $first_time"
echo "  End:   $last_time"

# =====================================================
# Overall Status
# =====================================================
echo ""
if [ "$error_count" -eq 0 ]; then
    echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  PIPELINE STATUS: HEALTHY ✅                 ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
else
    echo -e "${RED}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  PIPELINE STATUS: NEEDS ATTENTION ❌         ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════╝${NC}"
fi
