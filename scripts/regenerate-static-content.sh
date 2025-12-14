#!/bin/bash
# Regenerates static calendar product pages from local database
# Requires: Quarkus running locally on port 8030

set -e

BASE_URL="${BASE_URL:-http://localhost:8030}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATIC_DIR="$PROJECT_ROOT/src/main/resources/META-INF/resources/static/calendars"
DATA_DIR="$PROJECT_ROOT/src/main/resources/data"

echo "=== Regenerating Static Calendar Content ==="
echo "Base URL: $BASE_URL"
echo "Static dir: $STATIC_DIR"
echo "Data dir: $DATA_DIR"
echo ""

# Create directories
mkdir -p "$STATIC_DIR"
mkdir -p "$DATA_DIR"

# Download calendars.json with validation
echo "Downloading calendars.json..."
TEMP_FILE=$(mktemp)
HTTP_CODE=$(curl -s -w "%{http_code}" -o "$TEMP_FILE" "$BASE_URL/api/static-content/calendars.json")

if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: Server returned HTTP $HTTP_CODE"
    echo "Please ensure Quarkus is running: ./mvnw quarkus:dev"
    rm -f "$TEMP_FILE"
    exit 1
fi

# Verify it's valid JSON (starts with '[')
if ! head -c1 "$TEMP_FILE" | grep -q '\['; then
    echo "ERROR: Response is not valid JSON (got HTML error page?)"
    echo "Please ensure Quarkus is fully started and healthy"
    rm -f "$TEMP_FILE"
    exit 1
fi

mv "$TEMP_FILE" "$DATA_DIR/calendars.json"
echo "  -> Saved to $DATA_DIR/calendars.json"

# Parse slugs from JSON and download assets for each
echo ""
echo "Downloading calendar assets..."

# Use python to parse JSON (more reliable than jq which may not be installed)
SLUGS=$(python3 -c "
import json
with open('$DATA_DIR/calendars.json') as f:
    data = json.load(f)
    for item in data:
        print(item.get('slug', ''))
" 2>/dev/null | grep -v '^$')

if [ -z "$SLUGS" ]; then
    echo "No calendars found with slugs in database"
    exit 0
fi

for slug in $SLUGS; do
    echo ""
    echo "Processing: $slug"

    # Download SVG with validation
    echo "  Downloading SVG..."
    TEMP_SVG=$(mktemp)
    HTTP_CODE=$(curl -s -w "%{http_code}" -o "$TEMP_SVG" "$BASE_URL/api/static-content/calendars/${slug}.svg")
    if [ "$HTTP_CODE" = "200" ] && head -c5 "$TEMP_SVG" | grep -q '<svg\|<?xml'; then
        mv "$TEMP_SVG" "$STATIC_DIR/${slug}.svg"
        echo "    -> $STATIC_DIR/${slug}.svg ($(du -h "$STATIC_DIR/${slug}.svg" | cut -f1))"
    else
        echo "    ERROR: Failed to download SVG (HTTP $HTTP_CODE)"
        rm -f "$TEMP_SVG"
    fi

    # Download PNG with validation
    echo "  Downloading PNG..."
    TEMP_PNG=$(mktemp)
    HTTP_CODE=$(curl -s -w "%{http_code}" -o "$TEMP_PNG" "$BASE_URL/api/static-content/calendars/${slug}.png")
    if [ "$HTTP_CODE" = "200" ] && file "$TEMP_PNG" | grep -q 'PNG image'; then
        mv "$TEMP_PNG" "$STATIC_DIR/${slug}.png"
        echo "    -> $STATIC_DIR/${slug}.png ($(du -h "$STATIC_DIR/${slug}.png" | cut -f1))"
        # Show PNG dimensions
        if command -v sips &> /dev/null; then
            DIMS=$(sips -g pixelWidth -g pixelHeight "$STATIC_DIR/${slug}.png" 2>/dev/null | grep pixel | awk '{print $2}' | tr '\n' 'x' | sed 's/x$//')
            echo "    PNG dimensions: $DIMS"
        fi
    else
        echo "    ERROR: Failed to download PNG (HTTP $HTTP_CODE)"
        rm -f "$TEMP_PNG"
    fi
done

echo ""
echo "=== Done ==="
echo ""
echo "Generated files:"
ls -la "$STATIC_DIR"/*.{svg,png} 2>/dev/null || echo "  (no files)"
echo ""
echo "NOTE: Restart Quarkus dev server to pick up new static files"
