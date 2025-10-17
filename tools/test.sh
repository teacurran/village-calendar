#!/usr/bin/env bash

# tools/test.sh
# Purpose: Run project tests (Maven + npm tests)
# Ensures dependencies are installed before running tests

set -e  # Exit on error
set -u  # Exit on undefined variable
set -o pipefail  # Exit on pipe failure

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Project paths
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly FRONTEND_DIR="${PROJECT_ROOT}/src/main/webui"

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $*" >&2
}

# Run install script to ensure dependencies are up to date
ensure_dependencies() {
    log_step "Ensuring dependencies are up to date..."

    if [ -x "${SCRIPT_DIR}/install.sh" ]; then
        bash "${SCRIPT_DIR}/install.sh"
    else
        log_error "install.sh not found or not executable"
        exit 1
    fi
}

# Run Maven tests
run_maven_tests() {
    log_step "Running Maven tests..."

    cd "${PROJECT_ROOT}"

    # Use Maven wrapper if available, otherwise use system Maven
    local mvn_cmd
    if [ -x "./mvnw" ]; then
        mvn_cmd="./mvnw"
    else
        mvn_cmd="mvn"
    fi

    # Run tests
    if ${mvn_cmd} test; then
        log_info "Maven tests passed"
        return 0
    else
        log_error "Maven tests failed"
        return 1
    fi
}

# Run npm tests (if configured)
run_npm_tests() {
    log_step "Running npm tests..."

    if [ ! -d "${FRONTEND_DIR}" ]; then
        log_info "No frontend directory found, skipping npm tests"
        return 0
    fi

    cd "${FRONTEND_DIR}"

    # Check if test script exists in package.json
    if ! grep -q '"test":' package.json 2>/dev/null; then
        log_info "No test script found in package.json, skipping npm tests"
        return 0
    fi

    # Run tests
    if npm test 2>&1; then
        log_info "npm tests passed"
        return 0
    else
        local test_exit=$?
        # Check if the error was due to missing test script
        if [ ${test_exit} -eq 1 ]; then
            log_info "npm test script exists but no tests configured, skipping"
            return 0
        else
            log_error "npm tests failed"
            return 1
        fi
    fi
}

# Main execution
main() {
    log_info "Village Calendar - Test Suite"
    log_info "=============================="
    echo ""

    # Ensure dependencies are installed
    ensure_dependencies

    echo ""

    # Track test results
    local maven_status=0
    local npm_status=0

    # Run Maven tests
    run_maven_tests || maven_status=$?

    echo ""

    # Run npm tests
    run_npm_tests || npm_status=$?

    echo ""

    # Report results
    if [ ${maven_status} -eq 0 ] && [ ${npm_status} -eq 0 ]; then
        log_info "All tests passed successfully!"
        exit 0
    else
        log_error "Some tests failed"
        [ ${maven_status} -ne 0 ] && log_error "  - Maven tests failed"
        [ ${npm_status} -ne 0 ] && log_error "  - npm tests failed"
        exit 1
    fi
}

# Run main function
main "$@"
