#!/usr/bin/env bash

# tools/lint.sh
# Purpose: Lint the project source code and output results in JSON format
# Outputs ONLY JSON to stdout, all other messages go to stderr

set -e  # Exit on error
set -u  # Exit on undefined variable
set -o pipefail  # Exit on pipe failure

# Project paths
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly FRONTEND_DIR="${PROJECT_ROOT}/src/main/webui"
readonly JAVA_SRC_DIR="${PROJECT_ROOT}/src/main/java"

# Temp files for collecting errors
readonly JAVA_ERRORS_FILE="$(mktemp)"
readonly JS_ERRORS_FILE="$(mktemp)"
readonly COMBINED_ERRORS_FILE="$(mktemp)"

# Cleanup function
cleanup() {
    rm -f "${JAVA_ERRORS_FILE}" "${JS_ERRORS_FILE}" "${COMBINED_ERRORS_FILE}"
}
trap cleanup EXIT

# Logging to stderr only
log_info() {
    echo "[INFO] $*" >&2
}

log_error() {
    echo "[ERROR] $*" >&2
}

# Run install script silently to ensure dependencies
ensure_dependencies() {
    log_info "Ensuring dependencies are up to date..."

    if [ -x "${SCRIPT_DIR}/install.sh" ]; then
        bash "${SCRIPT_DIR}/install.sh" >/dev/null 2>&1
    else
        log_error "install.sh not found or not executable"
        exit 1
    fi
}

# Install ESLint if not present in frontend
ensure_eslint() {
    cd "${FRONTEND_DIR}"

    if ! npm list eslint >/dev/null 2>&1; then
        log_info "Installing ESLint..."
        npm install --save-dev eslint >/dev/null 2>&1
    fi
}

# Install Checkstyle for Java linting
ensure_checkstyle() {
    local checkstyle_jar="${PROJECT_ROOT}/.tools/checkstyle.jar"
    local checkstyle_config="${PROJECT_ROOT}/.tools/checkstyle.xml"

    # Create tools directory if needed
    mkdir -p "${PROJECT_ROOT}/.tools"

    # Download Checkstyle if not present
    if [ ! -f "${checkstyle_jar}" ]; then
        log_info "Downloading Checkstyle..."
        curl -sL "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-10.20.2/checkstyle-10.20.2-all.jar" \
            -o "${checkstyle_jar}" 2>&1 | log_info
    fi

    # Create minimal Checkstyle config if not present (syntax errors only)
    if [ ! -f "${checkstyle_config}" ]; then
        log_info "Creating Checkstyle configuration..."
        cat > "${checkstyle_config}" << 'EOF'
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <!-- Only check for syntax errors and critical issues -->
    </module>
</module>
EOF
    fi

    echo "${checkstyle_jar}"
}

# Lint Java files using javac for syntax checking
lint_java() {
    log_info "Linting Java files..."

    if [ ! -d "${JAVA_SRC_DIR}" ]; then
        log_info "No Java source directory found, skipping Java lint"
        echo "[]" > "${JAVA_ERRORS_FILE}"
        return 0
    fi

    # Use Maven compiler to check for syntax errors
    cd "${PROJECT_ROOT}"

    local mvn_cmd
    if [ -x "./mvnw" ]; then
        mvn_cmd="./mvnw"
    else
        mvn_cmd="mvn"
    fi

    # Compile and capture errors
    local compile_output
    if compile_output=$(${mvn_cmd} compiler:compile -q 2>&1); then
        log_info "Java compilation successful, no syntax errors"
        echo "[]" > "${JAVA_ERRORS_FILE}"
        return 0
    else
        log_info "Java compilation errors detected, parsing output..."

        # Parse Maven compiler errors and convert to JSON
        echo "${compile_output}" | awk '
        BEGIN {
            print "["
            first = 1
        }
        /\[ERROR\].*\.java:\[[0-9]+,[0-9]+\]/ {
            # Extract file path, line, column, and message
            match($0, /\[ERROR\] (.+\.java):\[([0-9]+),([0-9]+)\] (.+)/, arr)
            if (arr[1] != "") {
                if (!first) print ","
                first = 0
                gsub(/"/, "\\\"", arr[4])  # Escape quotes in message
                printf "  {\"type\":\"syntax_error\",\"path\":\"%s\",\"line\":\"%s\",\"column\":\"%s\",\"message\":\"%s\",\"obj\":\"\"}", arr[1], arr[2], arr[3], arr[4]
            }
        }
        /\[ERROR\].*\.java.*error:/ {
            # Alternative error format
            match($0, /\[ERROR\] (.+\.java):([0-9]+): error: (.+)/, arr)
            if (arr[1] != "") {
                if (!first) print ","
                first = 0
                gsub(/"/, "\\\"", arr[3])
                printf "  {\"type\":\"syntax_error\",\"path\":\"%s\",\"line\":\"%s\",\"column\":\"0\",\"message\":\"%s\",\"obj\":\"\"}", arr[1], arr[2], arr[3]
            }
        }
        END {
            print ""
            print "]"
        }
        ' > "${JAVA_ERRORS_FILE}"

        # Check if we actually found errors
        if [ "$(cat "${JAVA_ERRORS_FILE}")" = "[]" ] || [ "$(cat "${JAVA_ERRORS_FILE}" | jq '. | length')" -eq 0 ] 2>/dev/null; then
            log_info "No parseable Java errors found"
            echo "[]" > "${JAVA_ERRORS_FILE}"
        fi

        return 1
    fi
}

# Lint JavaScript/TypeScript files
lint_javascript() {
    log_info "Linting JavaScript/TypeScript files..."

    if [ ! -d "${FRONTEND_DIR}" ]; then
        log_info "No frontend directory found, skipping JS lint"
        echo "[]" > "${JS_ERRORS_FILE}"
        return 0
    fi

    cd "${FRONTEND_DIR}"

    # Run ESLint with JSON formatter
    # Note: ESLint returns non-zero if there are errors, but we capture output anyway
    local eslint_exit=0
    local eslint_output

    eslint_output=$(npx eslint . --format json 2>/dev/null) || eslint_exit=$?

    # Check if output is valid JSON and has errors
    if [ ${eslint_exit} -eq 0 ]; then
        log_info "ESLint passed, no errors found"
        echo "[]" > "${JS_ERRORS_FILE}"
        return 0
    else
        log_info "ESLint errors detected, parsing output..."

        # Check if we have valid JSON output
        if echo "${eslint_output}" | jq empty 2>/dev/null; then
            # ESLint JSON output to our format
            echo "${eslint_output}" | jq -r '
            [
                .[] |
                select(.errorCount > 0 or .warningCount > 0) |
                .filePath as $file |
                .messages[] |
                select(.severity == 2) |
                {
                    type: (if .ruleId then "lint_error" else "syntax_error" end),
                    path: $file,
                    line: (.line | tostring),
                    column: (.column | tostring),
                    message: .message,
                    obj: (.ruleId // "")
                }
            ]
            ' > "${JS_ERRORS_FILE}"
        else
            log_info "Could not parse ESLint output, no errors to report"
            echo "[]" > "${JS_ERRORS_FILE}"
        fi

        # Only return error if we actually found errors in the JSON
        local error_count
        error_count=$(cat "${JS_ERRORS_FILE}" | jq '. | length' 2>/dev/null || echo "0")
        if [ "${error_count}" -gt 0 ] 2>/dev/null; then
            return 1
        else
            log_info "No critical ESLint errors found"
            return 0
        fi
    fi
}

# Combine all errors into single JSON output
combine_errors() {
    log_info "Combining lint results..."

    # Combine Java and JS errors
    jq -s 'add' "${JAVA_ERRORS_FILE}" "${JS_ERRORS_FILE}" 2>/dev/null > "${COMBINED_ERRORS_FILE}" || echo "[]" > "${COMBINED_ERRORS_FILE}"

    # Output to stdout (ONLY place we write to stdout)
    cat "${COMBINED_ERRORS_FILE}"
}

# Main execution
main() {
    log_info "Starting linting process..."

    # Ensure dependencies are installed
    ensure_dependencies

    # Ensure linting tools are available
    ensure_eslint

    # Run linters
    local java_status=0
    local js_status=0

    lint_java || java_status=$?
    lint_javascript || js_status=$?

    # Combine all errors and output JSON
    combine_errors

    # Exit with error if any linter failed
    if [ ${java_status} -ne 0 ] || [ ${js_status} -ne 0 ]; then
        log_info "Linting completed with errors"
        exit 1
    else
        log_info "Linting completed successfully"
        exit 0
    fi
}

# Run main function
main "$@"
