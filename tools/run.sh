#!/usr/bin/env bash

# tools/run.sh
# Purpose: Run the Village Calendar application in development mode
# Ensures dependencies are installed before starting the application

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

# Run the application
run_application() {
    log_step "Starting Village Calendar application..."

    cd "${PROJECT_ROOT}"

    # Source environment configuration
    if [ -f "${PROJECT_ROOT}/.env.tools" ]; then
        # shellcheck disable=SC1091
        source "${PROJECT_ROOT}/.env.tools"
    fi

    # Use Maven wrapper if available, otherwise use system Maven
    local mvn_cmd
    if [ -x "./mvnw" ]; then
        mvn_cmd="./mvnw"
    else
        mvn_cmd="mvn"
    fi

    # Run Quarkus in dev mode (this also starts the Vue dev server via Quinoa)
    log_info "Running: ${mvn_cmd} quarkus:dev"
    log_info "Application will be available at: http://localhost:8030"
    log_info "GraphQL UI will be available at: http://localhost:8030/graphql-ui"
    log_info "Press Ctrl+C to stop the application"
    echo ""

    ${mvn_cmd} quarkus:dev
}

# Main execution
main() {
    log_info "Village Calendar - Development Mode"
    log_info "===================================="
    echo ""

    # Ensure dependencies are installed
    ensure_dependencies

    echo ""

    # Run the application
    run_application
}

# Run main function
main "$@"
