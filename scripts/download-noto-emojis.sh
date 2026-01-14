#!/bin/bash
# Download Noto Emoji SVGs for all emojis in the picker
# Source: https://github.com/adobe-fonts/noto-emoji-svg

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COLOR_DIR="$PROJECT_ROOT/src/main/resources/emoji-svg"
MONO_DIR="$PROJECT_ROOT/src/main/resources/emoji-svg-mono"

# Base URLs for raw SVG files from adobe-fonts/noto-emoji-svg
COLOR_BASE_URL="https://raw.githubusercontent.com/adobe-fonts/noto-emoji-svg/master/svg"
MONO_BASE_URL="https://raw.githubusercontent.com/adobe-fonts/noto-emoji-svg/master/svg_bw"

# Create directories if they don't exist
mkdir -p "$COLOR_DIR"
mkdir -p "$MONO_DIR"

# Function to convert emoji to Unicode filename
emoji_to_filename() {
    local emoji="$1"
    local filename=""

    # Use Python to convert emoji to codepoints
    filename=$(python3 -c "
import sys
emoji = sys.argv[1]
# Remove variation selectors
emoji = emoji.replace('\ufe0f', '')
codepoints = []
for char in emoji:
    cp = ord(char)
    # Skip zero-width joiners for filename, but include in sequence
    codepoints.append(format(cp, 'x'))
print('_'.join(codepoints))
" "$emoji")

    echo "$filename"
}

# Function to download an emoji SVG
download_emoji() {
    local emoji="$1"
    local codepoints=$(emoji_to_filename "$emoji")

    if [ -z "$codepoints" ]; then
        echo "  SKIP: Could not convert emoji to codepoints"
        return 1
    fi

    local color_filename="emoji_u${codepoints}.svg"
    local mono_filename="u${codepoints}.svg"
    local color_url="${COLOR_BASE_URL}/u${codepoints}.svg"
    local mono_url="${MONO_BASE_URL}/u${codepoints}.svg"

    local color_success=false
    local mono_success=false

    # Download color SVG if not exists
    if [ ! -f "$COLOR_DIR/$color_filename" ]; then
        if curl -sf "$color_url" -o "$COLOR_DIR/$color_filename" 2>/dev/null; then
            echo "  COLOR: Downloaded $color_filename"
            color_success=true
        else
            echo "  COLOR: Not found at $color_url"
        fi
    else
        echo "  COLOR: Already exists $color_filename"
        color_success=true
    fi

    # Download mono SVG if not exists
    if [ ! -f "$MONO_DIR/$mono_filename" ]; then
        if curl -sf "$mono_url" -o "$MONO_DIR/$mono_filename" 2>/dev/null; then
            echo "  MONO:  Downloaded $mono_filename"
            mono_success=true
        else
            echo "  MONO:  Not found at $mono_url"
        fi
    else
        echo "  MONO:  Already exists $mono_filename"
        mono_success=true
    fi

    if $color_success || $mono_success; then
        return 0
    else
        return 1
    fi
}

# All emojis from the picker (extracted from InlineEmojiPicker.vue)
EMOJIS=(
    # Smileys & People
    "😀" "😃" "😄" "😁" "😆" "😅" "🤣" "😂" "🙂" "😊"
    "😇" "🥰" "😍" "🤩" "😘" "😗" "😚" "😋" "😛" "🤪"
    "😎" "🤓" "🥳" "😏" "😌" "😴" "🤒" "🤕" "🤢" "🤮"
    "🤧" "🥵" "🥶" "😱" "😨" "👶" "👧" "🧒" "👦" "👩"
    "🧑" "👨" "👵" "🧓" "👴" "👸" "🤴" "🎅" "🤶" "🧙"
    "🧚" "🧛" "🧜" "🧝" "🧞" "👼" "🤰" "👪" "👫" "👭" "👬"

    # Celebrations
    "🎉" "🎊" "🎂" "🎁" "🎈" "🎄" "🎃" "🎆" "🎇" "🧨"
    "✨" "🎀" "🎗️" "🏆" "🥇" "🥈" "🥉" "🎖️" "🏅" "🎯"
    "🎪" "🎭" "🎨" "🎬" "🎤" "🎧" "🎼" "🎹" "🎸" "🎺"
    "🎻" "🥁" "🎷" "🪘" "🎵" "🎶" "🎟️" "🎫" "🎲" "🎮"
    "🃏" "🀄" "🎴" "🪅" "🪆" "🧸" "🪀" "🪁" "🔮" "🪄"

    # Hearts & Love
    "❤️" "🧡" "💛" "💚" "💙" "💜" "🖤" "🤍" "🤎" "💔"
    "❣️" "💕" "💞" "💓" "💗" "💖" "💘" "💝" "💟" "♥️"
    "😻" "💑" "💏" "💋" "👄" "🌹" "🥀" "💐" "💒" "👰"
    "🤵" "💍" "🫶" "🤗" "🥹"

    # Nature
    "🌸" "💮" "🏵️" "🌹" "🥀" "🌺" "🌻" "🌼" "🌷" "🌱"
    "🪴" "🌲" "🌳" "🌴" "🌵" "🌾" "🌿" "☘️" "🍀" "🍁"
    "🍂" "🍃" "🪹" "🪺" "🍄" "🐚" "🪸" "🪨" "🌍" "🌎"
    "🌏" "🌙" "🌚" "🌝" "🌞" "⭐" "🌟" "✨" "💫" "☀️"
    "🌤️" "⛅" "🌥️" "🌦️" "🌈" "☁️" "🌧️" "⛈️" "🌩️" "❄️"
    "🐶" "🐱" "🐭" "🐹" "🐰" "🦊" "🐻" "🐼" "🐨" "🦁"

    # Food & Drink
    "🍕" "🍔" "🍟" "🌭" "🍿" "🧂" "🥓" "🥚" "🍳" "🧇"
    "🥞" "🧈" "🥐" "🍞" "🥖" "🥨" "🧀" "🥗" "🥙" "🥪"
    "🌮" "🌯" "🫔" "🥫" "🍝" "🍜" "🍲" "🍛" "🍣" "🍱"
    "🥟" "🦪" "🍤" "🍙" "🍚" "🍘" "🍥" "🥠" "🥮" "🍢"
    "🍡" "🍧" "🍨" "🍦" "🥧" "🧁" "🍰" "🎂" "🍮" "🍭"
    "🍬" "🍫" "🍩" "🍪" "🌰" "🥜" "🍯" "🥛" "☕" "🍵"

    # Activities
    "⚽" "🏀" "🏈" "⚾" "🥎" "🎾" "🏐" "🏉" "🥏" "🎱"
    "🪀" "🏓" "🏸" "🏒" "🏑" "🥍" "🏏" "🪃" "🥅" "⛳"
    "🪁" "🏹" "🎣" "🤿" "🥊" "🥋" "🎽" "🛹" "🛼" "🛷"
    "⛸️" "🥌" "🎿" "⛷️" "🏂" "🪂" "🏋️" "🤼" "🤸" "🤺"
    "⛹️" "🤾" "🏌️" "🏇" "🧘" "🏄" "🏊" "🤽" "🚣" "🧗"
    "🚴" "🚵" "🏎️" "🏍️" "🤹" "🎪" "🎭" "🎨" "🎬" "📸"

    # Travel & Places
    "✈️" "🛫" "🛬" "🛩️" "💺" "🚀" "🛸" "🚁" "🛶" "⛵"
    "🚤" "🛥️" "🛳️" "⛴️" "🚢" "🚂" "🚃" "🚄" "🚅" "🚆"
    "🚇" "🚈" "🚉" "🚊" "🚝" "🚞" "🚋" "🚌" "🚍" "🚎"
    "🚐" "🚑" "🚒" "🚓" "🚔" "🚕" "🚖" "🚗" "🚘" "🚙"
    "🛻" "🚚" "🚛" "🚜" "🏎️" "🏍️" "🛵" "🛺" "🚲" "🛴"
    "🗼" "🗽" "🏰" "🏯" "🏟️" "🎡" "🎢" "🎠" "⛲" "⛱️"

    # Symbols
    "⭐" "🌟" "✨" "💫" "⚡" "🔥" "💥" "☄️" "🌈" "☀️"
    "🌙" "💧" "🌊" "💨" "🍀" "🔔" "🎵" "🎶" "💡" "🔑"
    "🗝️" "🔒" "🔓" "📌" "📍" "✅" "❌" "❓" "❗" "💯"
    "🔢" "🔤" "🅰️" "🅱️" "🆎" "🅾️" "🆘" "⛔" "🚫" "❤️"
    "🧡" "💛" "💚" "💙" "💜" "🖤" "🤍" "🤎" "♠️" "♣️"
    "♥️" "♦️" "🔴" "🟠" "🟡" "🟢" "🔵" "🟣" "⚫" "⚪"
)

echo "Downloading Noto Emoji SVGs..."
echo "Color SVGs: $COLOR_DIR"
echo "Mono SVGs:  $MONO_DIR"
echo ""

downloaded=0
failed=0

for emoji in "${EMOJIS[@]}"; do
    echo "Processing: $emoji"
    if download_emoji "$emoji"; then
        ((downloaded++))
    else
        ((failed++))
    fi
done

echo ""
echo "Done! Downloaded/verified: $downloaded, Failed: $failed"
