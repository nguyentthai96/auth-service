#!/usr/bin/env bash
# =====================================================
# Hermes Dev Pipeline — Build, Lint, Test, Analyze
# FR-008/009/010/012/016 Full Pipeline Automation
# =====================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
PIPELINE_LOG="$LOG_DIR/pipeline.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

mkdir -p "$LOG_DIR"

log() {
    local level=$1
    shift
    local msg="[$TIMESTAMP] [$level] $*"
    echo -e "$msg" | tee -a "$PIPELINE_LOG"
}

step() {
    echo -e "\n${BLUE}━━━ STEP: $1 ━━━${NC}"
    log "STEP" "$1"
}

success() {
    echo -e "${GREEN}✅ $1${NC}"
    log "OK" "$1"
}

fail() {
    echo -e "${RED}❌ $1${NC}"
    log "FAIL" "$1"
}

warn() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    log "WARN" "$1"
}

# =====================================================
echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════╗"
echo "║        HERMES DEV PIPELINE v1.0              ║"
echo "║  auth-service + system-admin-service          ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

log "INFO" "Pipeline started at $TIMESTAMP"

SERVICES_ROOT="$(cd "$PROJECT_ROOT/.." && pwd)"
AUTH_SERVICE="$SERVICES_ROOT/auth-service"
ADMIN_SERVICE="$SERVICES_ROOT/system-admin-service"

ERRORS=0

# =====================================================
# STEP 1: Lint — Check Kotlin code style
# =====================================================
step "1/5 — Lint Check"

for service in "$AUTH_SERVICE" "$ADMIN_SERVICE"; do
    service_name=$(basename "$service")
    if [ -f "$service/gradlew" ]; then
        log "INFO" "Checking $service_name..."
        if cd "$service" && ./gradlew ktlintCheck 2>>"$PIPELINE_LOG"; then
            success "$service_name lint passed"
        else
            warn "$service_name lint issues found (non-blocking)"
        fi
    else
        warn "No gradlew found in $service_name"
    fi
done

# =====================================================
# STEP 2: Compile — Build without tests
# =====================================================
step "2/5 — Compile"

for service in "$AUTH_SERVICE" "$ADMIN_SERVICE"; do
    service_name=$(basename "$service")
    if [ -f "$service/gradlew" ]; then
        log "INFO" "Building $service_name..."
        if cd "$service" && ./gradlew compileKotlin 2>>"$PIPELINE_LOG"; then
            success "$service_name compiled"
        else
            fail "$service_name compile FAILED"
            ERRORS=$((ERRORS + 1))
        fi
    fi
done

# =====================================================
# STEP 3: Test — Run unit tests
# =====================================================
step "3/5 — Test"

for service in "$AUTH_SERVICE" "$ADMIN_SERVICE"; do
    service_name=$(basename "$service")
    if [ -f "$service/gradlew" ]; then
        log "INFO" "Testing $service_name..."
        if cd "$service" && ./gradlew test 2>>"$PIPELINE_LOG"; then
            success "$service_name tests passed"
        else
            fail "$service_name tests FAILED"
            ERRORS=$((ERRORS + 1))
        fi
    fi
done

# =====================================================
# STEP 4: Build — Full build (JAR)
# =====================================================
step "4/5 — Build JAR"

for service in "$AUTH_SERVICE" "$ADMIN_SERVICE"; do
    service_name=$(basename "$service")
    if [ -f "$service/gradlew" ]; then
        log "INFO" "Building JAR for $service_name..."
        if cd "$service" && ./gradlew build -x test 2>>"$PIPELINE_LOG"; then
            success "$service_name JAR built"
        else
            fail "$service_name JAR build FAILED"
            ERRORS=$((ERRORS + 1))
        fi
    fi
done

# =====================================================
# STEP 5: Migration Check — Verify Flyway scripts
# =====================================================
step "5/5 — Migration Verification"

for service in "$AUTH_SERVICE" "$ADMIN_SERVICE"; do
    service_name=$(basename "$service")
    migration_dir="$service/src/main/resources/db/migration"
    if [ -d "$migration_dir" ]; then
        count=$(ls -1 "$migration_dir"/V*.sql 2>/dev/null | wc -l)
        log "INFO" "$service_name: $count migration files found"

        # Check for version conflicts
        versions=$(ls -1 "$migration_dir"/V*.sql 2>/dev/null | sed 's/.*V\([0-9]*\).*/\1/' | sort -n | uniq -d)
        if [ -n "$versions" ]; then
            fail "$service_name has duplicate migration versions: $versions"
            ERRORS=$((ERRORS + 1))
        else
            success "$service_name: $count migrations, no conflicts"
        fi
    fi
done

# =====================================================
# Summary
# =====================================================
echo -e "\n${BLUE}━━━ PIPELINE SUMMARY ━━━${NC}"

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  ✅ ALL CHECKS PASSED                        ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
    log "INFO" "Pipeline PASSED — 0 errors"
else
    echo -e "${RED}╔══════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  ❌ $ERRORS ERROR(S) FOUND                       ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════╝${NC}"
    log "ERROR" "Pipeline FAILED — $ERRORS errors"
fi

log "INFO" "Pipeline finished at $(date '+%Y-%m-%d %H:%M:%S')"
echo -e "\n📝 Full log: $PIPELINE_LOG"
exit $ERRORS
