# Holiday Emoji Master List

This document tracks all emoji assignments for holiday sets in Village Calendar.
It serves as the master reference for emoji selection and SVG availability.

**Last Updated:** 2025-12-07

## SVG Locations

- **Color SVGs:** `src/main/resources/emoji-svg/` (from Google's Noto Color Emoji)
- **Mono SVGs:** `src/main/resources/emoji-svg-mono/` (from Adobe's Noto Emoji SVG)
- **Emoji Mappings:** `EmojiSvgService.java` - `EMOJI_TO_FILENAME` map
- **Holiday Assignments:** `CalendarRenderingService.java` - `getHolidaysWithEmoji()` method

---

## US Federal Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| New Year's Day | Jan 1 | 🎉 | U+1F389 | ✅ | ✅ | OK |
| MLK Day | 3rd Mon Jan | 🕊️ | U+1F54A | ✅ | ✅ | OK |
| Presidents' Day | 3rd Mon Feb | 🏛️ | U+1F3DB | ✅ | ✅ | OK |
| Memorial Day | Last Mon May | 🎖️ | U+1F396 | ✅ | ✅ | OK |it lholid
| Independence Day | Jul 4 | 🇺🇸 | U+1F1FA_1F1F8 | ✅ | ✅ | OK |
| Labor Day | 1st Mon Sep | 👷 | U+1F477 | ✅ | ✅ | OK |
| Halloween | Oct 31 | 🎃 | U+1F383 | ✅ | ✅ | OK |
| Veterans Day | Nov 11 | 🎖️ | U+1F396 | ✅ | ✅ | OK |
| Thanksgiving | 4th Thu Nov | 🦃 | U+1F983 | ✅ | ✅ | OK |
| Christmas | Dec 25 | 🎄 | U+1F384 | ✅ | ✅ | OK |

---

## Christian Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Epiphany | Jan 6 | ⭐ | U+2B50 | ✅ | ✅ | OK |
| Ash Wednesday | Easter - 46 | ✝️ | U+271D | ✅ | ✅ | OK |
| Palm Sunday | Easter - 7 | 🌿 | U+1F33F | ✅ | ✅ | OK |
| Good Friday | Easter - 2 | 🐟 | U+1F41F | ✅ | ✅ | OK |
| Easter Sunday | Calculated | 🐑 | U+1F411 | ✅ | ✅ | OK |
| Ascension Day | Easter + 39 | ☁️ | U+2601 | ✅ | ✅ | OK |
| Pentecost | Easter + 49 | 🕊️ | U+1F54A | ✅ | ✅ | OK |
| All Saints Day | Nov 1 | 👼 | U+1F47C | ✅ | ✅ | OK |
| Christmas Eve | Dec 24 | 🕯️ | U+1F56F | ✅ | ✅ | OK |
| Christmas | Dec 25 | 🎄 | U+1F384 | ✅ | ✅ | OK |

---

## Jewish Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Purim | 14 Adar | 🎭 | U+1F3AD | ✅ | ✅ | OK |
| Passover (Start) | 15 Nisan | 🍷 | U+1F377 | ✅ | ✅ | OK |
| Passover (End) | 22 Nisan | 🍷 | U+1F377 | ✅ | ✅ | OK |
| Shavuot | 6 Sivan | 📜 | U+1F4DC | ✅ | ✅ | OK |
| Rosh Hashanah | 1-2 Tishrei | 🍎 | U+1F34E | ✅ | ✅ | OK |
| Yom Kippur | 10 Tishrei | ✡️ | U+2721 | ✅ | ✅ | OK |
| Sukkot | 15 Tishrei | 🌿 | U+1F33F | ✅ | ✅ | OK |
| Simchat Torah | 22 Tishrei | 📜 | U+1F4DC | ✅ | ✅ | OK |
| Chanukah | 25 Kislev | 🕎 | U+1F54E | ✅ | ✅ | OK |

---

## Islamic Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Islamic New Year | 1 Muharram | 🌙 | U+1F319 | ✅ | ✅ | OK |
| Ashura | 10 Muharram | 🤲 | U+1F932 | ✅ | ✅ | OK |
| Mawlid al-Nabi | 12 Rabi al-Awwal | ☪️ | U+262A | ✅ | ✅ | OK |
| Ramadan Begins | 1 Ramadan | 🌙 | U+1F319 | ✅ | ✅ | OK |
| Laylat al-Qadr | 27 Ramadan | ✨ | U+2728 | ✅ | ✅ | OK |
| Eid al-Fitr | 1 Shawwal | 🎉 | U+1F389 | ✅ | ✅ | OK |
| Eid al-Adha | 10 Dhul Hijjah | 🐑 | U+1F411 | ✅ | ✅ | OK |

---

## Hindu Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Makar Sankranti | Jan 14 | 🪁 | U+1FA81 | ✅ | ✅ | OK |
| Holi | Full Moon Phalguna | 🎨 | U+1F3A8 | ✅ | ✅ | OK |
| Ram Navami | 9th Chaitra | 🏹 | U+1F3F9 | ✅ | ✅ | OK |
| Janmashtami | 8th Bhadrapada | 🪈 | U+1FA88 | ✅ | ⚠️ | Grayscale fallback* |
| Ganesh Chaturthi | 4th Bhadrapada | 🐘 | U+1F418 | ✅ | ✅ | OK |
| Navaratri | 1st Ashvin | 💃 | U+1F483 | ✅ | ✅ | OK |
| Dussehra | 10th Ashvin | 🏹 | U+1F3F9 | ✅ | ✅ | OK |
| Diwali | New Moon Kartik | 🪔 | U+1FA94 | ✅ | ✅ | OK |

---

## Chinese Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Chinese New Year | 2nd New Moon after Winter Solstice | 🧧 | U+1F9E7 | ✅ | ✅ | OK |
| Lantern Festival | CNY + 14 days | 🏮 | U+1F3EE | ✅ | ✅ | OK |
| Qingming Festival | Spring Equinox + 15 | 🪦 | U+1FAA6 | ✅ | ✅ | OK |
| Dragon Boat Festival | 5th day 5th lunar month | 🐉 | U+1F409 | ✅ | ✅ | OK |
| Mid-Autumn Festival | 15th 8th lunar month | 🥮 | U+1F96E | ✅ | ✅ | OK |
| Double Ninth Festival | 9th 9th lunar month | 🏔️ | U+1F3D4 | ✅ | ✅ | OK |

---

## Canadian Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| New Year's Day | Jan 1 | 🎉 | U+1F389 | ✅ | ✅ | OK |
| Family Day | 3rd Mon Feb | 👨‍👩‍👧‍👦 | U+1F468_200D_1F469_200D_1F467_200D_1F466 | ✅ | ✅ | OK |
| Good Friday | Easter - 2 | 🐟 | U+1F41F | ✅ | ✅ | OK |
| Victoria Day | Mon before May 25 | 👑 | U+1F451 | ✅ | ✅ | OK |
| Canada Day | Jul 1 | 🍁 | U+1F341 | ✅ | ✅ | OK |
| Labour Day | 1st Mon Sep | 👷 | U+1F477 | ✅ | ✅ | OK |
| Thanksgiving | 2nd Mon Oct | 🦃 | U+1F983 | ✅ | ✅ | OK |
| Remembrance Day | Nov 11 | 🎖️ | U+1F396 | ✅ | ✅ | OK |
| Christmas | Dec 25 | 🎄 | U+1F384 | ✅ | ✅ | OK |
| Boxing Day | Dec 26 | 🎁 | U+1F381 | ✅ | ✅ | OK |

---

## UK Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| New Year's Day | Jan 1 | 🎉 | U+1F389 | ✅ | ✅ | OK |
| Good Friday | Easter - 2 | 🐟 | U+1F41F | ✅ | ✅ | OK |
| Easter Monday | Easter + 1 | 🐰 | U+1F430 | ✅ | ✅ | OK |
| Early May Bank Holiday | 1st Mon May | 🌸 | U+1F338 | ✅ | ✅ | OK |
| Spring Bank Holiday | Last Mon May | 🌷 | U+1F337 | ✅ | ✅ | OK |
| Summer Bank Holiday | Last Mon Aug | ☀️ | U+2600 | ✅ | ✅ | OK |
| Christmas | Dec 25 | 🎄 | U+1F384 | ✅ | ✅ | OK |
| Boxing Day | Dec 26 | 🎁 | U+1F381 | ✅ | ✅ | OK |

---

## Mexican Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Día de los Reyes | Jan 6 | 👑 | U+1F451 | ✅ | ✅ | OK |
| Constitution Day | Feb 5 | 📜 | U+1F4DC | ✅ | ✅ | OK |
| Benito Juárez | Mar 21 | ⚖️ | U+2696 | ✅ | ✅ | OK |
| Cinco de Mayo | May 5 | 🇲🇽 | U+1F1F2_1F1FD | ✅ | ✅ | OK |
| Independence Day | Sep 16 | 🎉 | U+1F389 | ✅ | ✅ | OK |
| Día de los Muertos | Nov 1-2 | 💀 | U+1F480 | ✅ | ✅ | OK |
| Revolution Day | Nov 20 | 🎖️ | U+1F396 | ✅ | ✅ | OK |
| Virgen de Guadalupe | Dec 12 | 🌹 | U+1F339 | ✅ | ✅ | OK |
| Las Posadas | Dec 16 | 🕯️ | U+1F56F | ✅ | ✅ | OK |

---

## Pagan/Wiccan Holidays (Wheel of the Year)

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Imbolc | Feb 1 | 🕯️ | U+1F56F | ✅ | ✅ | OK |
| Ostara | Spring Equinox | 🐣 | U+1F423 | ✅ | ✅ | OK |
| Beltane | May 1 | 🔥 | U+1F525 | ✅ | ✅ | OK |
| Litha | Summer Solstice | ☀️ | U+2600 | ✅ | ✅ | OK |
| Lughnasadh | Aug 1 | 🌾 | U+1F33E | ✅ | ✅ | OK |
| Mabon | Autumn Equinox | 🍂 | U+1F342 | ✅ | ✅ | OK |
| Samhain | Oct 31 | 🎃 | U+1F383 | ✅ | ✅ | OK |
| Yule | Winter Solstice | 🌲 | U+1F332 | ✅ | ✅ | OK |

---

## Secular/Fun Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| Groundhog Day | Feb 2 | 🦫 | U+1F9AB | ✅ | ✅ | OK |
| Valentine's Day | Feb 14 | ❤️ | U+2764 | ✅ | ✅ | OK |
| St. Patrick's Day | Mar 17 | ☘️ | U+2618 | ✅ | ✅ | OK |
| April Fools' Day | Apr 1 | 🃏 | U+1F0CF | ✅ | ✅ | OK |
| Earth Day | Apr 22 | 🌍 | U+1F30D | ✅ | ✅ | OK |
| Cinco de Mayo | May 5 | 🌮 | U+1F32E | ✅ | ✅ | OK |
| Mother's Day | 2nd Sun May | 💐 | U+1F490 | ✅ | ✅ | OK |
| Father's Day | 3rd Sun Jun | 👔 | U+1F454 | ✅ | ✅ | OK |
| Pride Month | Jun 1 | 🏳️‍🌈 | U+1F3F3_200D_1F308 | ✅ | ✅ | OK |
| Independence Day | Jul 4 | 🦅 | U+1F985 | ✅ | ✅ | OK |
| Halloween | Oct 31 | 🎃 | U+1F383 | ✅ | ✅ | OK |
| Black Friday | Day after Thanksgiving | 🛒 | U+1F6D2 | ✅ | ✅ | OK |
| Kwanzaa | Dec 26 | 🕯️ | U+1F56F | ✅ | ✅ | OK |
| New Year's Eve | Dec 31 | 🍾 | U+1F37E | ✅ | ✅ | OK |

---

## Major World Holidays

| Holiday | Date | Emoji | Unicode | Color SVG | Mono SVG | Status |
|---------|------|-------|---------|-----------|----------|--------|
| New Year's Day | Jan 1 | 🎉 | U+1F389 | ✅ | ✅ | OK |
| Valentine's Day | Feb 14 | ❤️ | U+2764 | ✅ | ✅ | OK |
| St. Patrick's Day | Mar 17 | ☘️ | U+2618 | ✅ | ✅ | OK |
| Easter | Calculated | 🐰 | U+1F430 | ✅ | ✅ | OK |
| Earth Day | Apr 22 | 🌍 | U+1F30D | ✅ | ✅ | OK |
| Workers' Day | May 1 | 👷 | U+1F477 | ✅ | ✅ | OK |
| Halloween | Oct 31 | 🎃 | U+1F383 | ✅ | ✅ | OK |
| Christmas | Dec 25 | 🎄 | U+1F384 | ✅ | ✅ | OK |
| New Year's Eve | Dec 31 | 🎉 | U+1F389 | ✅ | ✅ | OK |

---

## Adding New Emojis

To add a new emoji to the system:

1. **Download SVGs:**
   - Color: `https://raw.githubusercontent.com/googlefonts/noto-emoji/main/svg/emoji_u{codepoint}.svg`
   - Mono: `https://raw.githubusercontent.com/adobe-fonts/noto-emoji-svg/main/svg_bw/u{codepoint}.svg`

2. **Add to Mapping:**
   Update `EmojiSvgService.java`:
   ```java
   EMOJI_TO_FILENAME.put("emoji", "emoji_u{codepoint}");
   ```

3. **Update Holiday Assignment:**
   Update `CalendarRenderingService.java` in `getHolidaysWithEmoji()` method.

4. **Update This Document:**
   Add the new emoji to the appropriate holiday set table above.

---

## Notes

**\* Grayscale fallback:** Some newer emojis (Unicode 14.0+) don't have monochrome SVGs in Adobe's repository. The system automatically applies a grayscale filter to the color SVG as fallback.

**Catholic vs Christian Holidays:** The current "Christian" set is ecumenical (shared across denominations). A dedicated "Catholic" set would add:
- Marian Feasts: Immaculate Conception (Dec 8), Assumption (Aug 15), Annunciation (Mar 25)
- Holy Days of Obligation: Solemnity of Mary (Jan 1), Corpus Christi
- Additional observances: Mardi Gras, Holy Thursday, Holy Saturday, Divine Mercy Sunday
- Saints' Days: St. Joseph (Mar 19), St. Peter & Paul (Jun 29), All Souls (Nov 2)

---

## Emoji Selection Guidelines

- **Religious symbols:** Use representative icons (🐑 for Easter/Lamb of God, 🐟 for Good Friday/Ichthys)
- **Cultural holidays:** Use culturally significant symbols (🍁 for Canada Day, 🇲🇽 for Cinco de Mayo)
- **Seasonal holidays:** Use nature/seasonal icons (🌿 for spring, 🍂 for autumn)
- **Avoid:** Overly specific religious symbols unless universally recognized
- **Prefer:** Icons that render well at small sizes and in both color and monochrome
