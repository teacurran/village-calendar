# Emoji Picker Monochrome Support

This document describes the emoji SVG support for the calendar's emoji picker and custom color rendering.

## Overview

The calendar uses **Noto Emoji SVGs** from [adobe-fonts/noto-emoji-svg](https://github.com/adobe-fonts/noto-emoji-svg) for rendering emojis:

- **Color SVGs** (`emoji-svg/`) - Used when emojis are rendered in the main calendar display
- **Monochrome SVGs** (`emoji-svg-mono/`) - Used for the color picker, allowing custom color tinting

Both sets are required because the calendar renders emojis as embedded SVGs (not text) for PDF/print output compatibility.

## Current Coverage

| Resource | Count |
|----------|------:|
| Color SVGs | 451 |
| Mono SVGs | 450 |
| Picker Emojis | 452 |
| **Mono Coverage** | **~99.6%** |

Nearly all emojis in the picker have both color and monochrome variants available.

## Emoji Categories

All picker categories have full or near-full mono support:

| Category | Total | With Mono | Coverage |
|----------|------:|----------:|---------:|
| Smileys & People | 62 | 62 | 100% |
| Celebrations | 50 | 50 | 100% |
| Hearts & Love | 40 | 40 | 100% |
| Nature | 60 | 60 | 100% |
| Food & Drink | 60 | 60 | 100% |
| Activities | 60 | 60 | 100% |
| Travel & Places | 60 | 60 | 100% |
| Symbols | 60 | 58 | 97% |

## Fallback Behavior

For the rare cases where a mono SVG is not available:

1. The color picker will display a text-based emoji with a grayscale CSS filter
2. The user can still select the emoji, but custom color tinting won't apply
3. The color version will still render correctly in the main calendar

## How to Add New Emojis

1. Find the emoji's Unicode codepoint(s)
2. Download from Noto repository:
   - Color: `https://raw.githubusercontent.com/adobe-fonts/noto-emoji-svg/master/svg/u{codepoints}.svg`
   - Mono: `https://raw.githubusercontent.com/adobe-fonts/noto-emoji-svg/master/svg_bw/u{codepoints}.svg`
3. Save to appropriate directories:
   - Color: `src/main/resources/emoji-svg/emoji_u{codepoints}.svg`
   - Mono: `src/main/resources/emoji-svg-mono/u{codepoints}.svg`
4. Add mapping to `EmojiSvgService.EMOJI_TO_FILENAME`

### Download Script

Use the provided script to download all picker emojis:

```bash
./scripts/download-noto-emojis.sh
```

This downloads both color and mono variants for all emojis defined in the picker.

## Technical Details

### File Naming Convention

- Color files: `emoji_u{codepoint}.svg` (e.g., `emoji_u1f389.svg` for 🎉)
- Mono files: `u{codepoint}.svg` (e.g., `u1f389.svg` for 🎉)

### Multi-codepoint Emojis

Some emojis use multiple codepoints joined together:
- ZWJ sequences (e.g., family emojis): `u{cp1}_200d_{cp2}.svg`
- Variation selectors are stripped (FE0F removed)

### EmojiSvgService

The `EmojiSvgService.java` class manages emoji-to-filename mapping:

```java
// Example mappings
EMOJI_TO_FILENAME.put("🎉", "emoji_u1f389"); // Party Popper
EMOJI_TO_FILENAME.put("🎂", "emoji_u1f382"); // Birthday Cake
EMOJI_TO_FILENAME.put("❤️", "emoji_u2764");  // Red Heart
```

The service provides:
- `getColorSvgPath(emoji)` - Returns path to color SVG
- `getMonoSvgPath(emoji)` - Returns path to mono SVG (or null if unavailable)
- `hasMonoSupport(emoji)` - Checks if mono variant exists
