<template>
  <Popover
    ref="popoverRef"
    append-to="self"
    :pt="{ root: { class: 'inline-emoji-popover' } }"
  >
    <div class="emoji-picker-content">
      <!-- Category tabs -->
      <div class="emoji-categories">
        <button
          v-for="category in categories"
          :key="category.id"
          class="category-btn"
          :class="{ active: activeCategory === category.id }"
          :title="category.name"
          @click="activeCategory = category.id"
        >
          {{ category.icon }}
        </button>
      </div>

      <!-- Emoji grid -->
      <div class="emoji-grid">
        <button
          v-for="emoji in currentEmojis"
          :key="emoji"
          class="emoji-btn"
          @click="selectEmoji(emoji)"
        >
          {{ emoji }}
        </button>
      </div>
    </div>
  </Popover>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import Popover from "primevue/popover";

const emit = defineEmits<{
  select: [emoji: string];
}>();

const popoverRef = ref();
const activeCategory = ref("smileys");

// Emoji categories with common emojis
const categories = [
  { id: "smileys", name: "Smileys & People", icon: "😀" },
  { id: "celebrations", name: "Celebrations", icon: "🎉" },
  { id: "hearts", name: "Hearts & Love", icon: "❤️" },
  { id: "nature", name: "Nature", icon: "🌸" },
  { id: "food", name: "Food & Drink", icon: "🍕" },
  { id: "activities", name: "Activities", icon: "⚽" },
  { id: "travel", name: "Travel", icon: "✈️" },
  { id: "symbols", name: "Symbols", icon: "⭐" },
];

const emojiData: Record<string, string[]> = {
  smileys: [
    "😀",
    "😃",
    "😄",
    "😁",
    "😆",
    "😅",
    "🤣",
    "😂",
    "🙂",
    "😊",
    "😇",
    "🥰",
    "😍",
    "🤩",
    "😘",
    "😗",
    "😚",
    "😋",
    "😛",
    "🤪",
    "😎",
    "🤓",
    "🥳",
    "😏",
    "😌",
    "😴",
    "🤒",
    "🤕",
    "🤢",
    "🤮",
    "🤧",
    "🥵",
    "🥶",
    "😱",
    "😨",
    "👶",
    "👧",
    "🧒",
    "👦",
    "👩",
    "🧑",
    "👨",
    "👵",
    "🧓",
    "👴",
    "👸",
    "🤴",
    "🎅",
    "🤶",
    "🧙",
    "🧚",
    "🧛",
    "🧜",
    "🧝",
    "🧞",
    "👼",
    "🤰",
    "👪",
    "👫",
    "👭",
    "👬",
  ],
  celebrations: [
    "🎉",
    "🎊",
    "🎂",
    "🎁",
    "🎈",
    "🎄",
    "🎃",
    "🎆",
    "🎇",
    "🧨",
    "✨",
    "🎀",
    "🎗️",
    "🏆",
    "🥇",
    "🥈",
    "🥉",
    "🎖️",
    "🏅",
    "🎯",
    "🎪",
    "🎭",
    "🎨",
    "🎬",
    "🎤",
    "🎧",
    "🎼",
    "🎹",
    "🎸",
    "🎺",
    "🎻",
    "🥁",
    "🎷",
    "🪘",
    "🎵",
    "🎶",
    "🎟️",
    "🎫",
    "🎲",
    "🎮",
    "🃏",
    "🀄",
    "🎴",
    "🪅",
    "🪆",
    "🧸",
    "🪀",
    "🪁",
    "🔮",
    "🪄",
  ],
  hearts: [
    "❤️",
    "🧡",
    "💛",
    "💚",
    "💙",
    "💜",
    "🖤",
    "🤍",
    "🤎",
    "💔",
    "❣️",
    "💕",
    "💞",
    "💓",
    "💗",
    "💖",
    "💘",
    "💝",
    "💟",
    "♥️",
    "😻",
    "💑",
    "💏",
    "💋",
    "👄",
    "🌹",
    "🥀",
    "💐",
    "💒",
    "👰",
    "🤵",
    "💍",
    "🫶",
    "🤗",
    "🥹",
    "😍",
    "🥰",
    "😘",
    "😚",
    "😗",
  ],
  nature: [
    "🌸",
    "💮",
    "🏵️",
    "🌹",
    "🥀",
    "🌺",
    "🌻",
    "🌼",
    "🌷",
    "🌱",
    "🪴",
    "🌲",
    "🌳",
    "🌴",
    "🌵",
    "🌾",
    "🌿",
    "☘️",
    "🍀",
    "🍁",
    "🍂",
    "🍃",
    "🪹",
    "🪺",
    "🍄",
    "🐚",
    "🪸",
    "🪨",
    "🌍",
    "🌎",
    "🌏",
    "🌙",
    "🌚",
    "🌝",
    "🌞",
    "⭐",
    "🌟",
    "✨",
    "💫",
    "☀️",
    "🌤️",
    "⛅",
    "🌥️",
    "🌦️",
    "🌈",
    "☁️",
    "🌧️",
    "⛈️",
    "🌩️",
    "❄️",
    "🐶",
    "🐱",
    "🐭",
    "🐹",
    "🐰",
    "🦊",
    "🐻",
    "🐼",
    "🐨",
    "🦁",
  ],
  food: [
    "🍕",
    "🍔",
    "🍟",
    "🌭",
    "🍿",
    "🧂",
    "🥓",
    "🥚",
    "🍳",
    "🧇",
    "🥞",
    "🧈",
    "🥐",
    "🍞",
    "🥖",
    "🥨",
    "🧀",
    "🥗",
    "🥙",
    "🥪",
    "🌮",
    "🌯",
    "🫔",
    "🥫",
    "🍝",
    "🍜",
    "🍲",
    "🍛",
    "🍣",
    "🍱",
    "🥟",
    "🦪",
    "🍤",
    "🍙",
    "🍚",
    "🍘",
    "🍥",
    "🥠",
    "🥮",
    "🍢",
    "🍡",
    "🍧",
    "🍨",
    "🍦",
    "🥧",
    "🧁",
    "🍰",
    "🎂",
    "🍮",
    "🍭",
    "🍬",
    "🍫",
    "🍩",
    "🍪",
    "🌰",
    "🥜",
    "🍯",
    "🥛",
    "☕",
    "🍵",
  ],
  activities: [
    "⚽",
    "🏀",
    "🏈",
    "⚾",
    "🥎",
    "🎾",
    "🏐",
    "🏉",
    "🥏",
    "🎱",
    "🪀",
    "🏓",
    "🏸",
    "🏒",
    "🏑",
    "🥍",
    "🏏",
    "🪃",
    "🥅",
    "⛳",
    "🪁",
    "🏹",
    "🎣",
    "🤿",
    "🥊",
    "🥋",
    "🎽",
    "🛹",
    "🛼",
    "🛷",
    "⛸️",
    "🥌",
    "🎿",
    "⛷️",
    "🏂",
    "🪂",
    "🏋️",
    "🤼",
    "🤸",
    "🤺",
    "⛹️",
    "🤾",
    "🏌️",
    "🏇",
    "🧘",
    "🏄",
    "🏊",
    "🤽",
    "🚣",
    "🧗",
    "🚴",
    "🚵",
    "🏎️",
    "🏍️",
    "🤹",
    "🎪",
    "🎭",
    "🎨",
    "🎬",
    "📸",
  ],
  travel: [
    "✈️",
    "🛫",
    "🛬",
    "🛩️",
    "💺",
    "🚀",
    "🛸",
    "🚁",
    "🛶",
    "⛵",
    "🚤",
    "🛥️",
    "🛳️",
    "⛴️",
    "🚢",
    "🚂",
    "🚃",
    "🚄",
    "🚅",
    "🚆",
    "🚇",
    "🚈",
    "🚉",
    "🚊",
    "🚝",
    "🚞",
    "🚋",
    "🚌",
    "🚍",
    "🚎",
    "🚐",
    "🚑",
    "🚒",
    "🚓",
    "🚔",
    "🚕",
    "🚖",
    "🚗",
    "🚘",
    "🚙",
    "🛻",
    "🚚",
    "🚛",
    "🚜",
    "🏎️",
    "🏍️",
    "🛵",
    "🛺",
    "🚲",
    "🛴",
    "🗼",
    "🗽",
    "🏰",
    "🏯",
    "🏟️",
    "🎡",
    "🎢",
    "🎠",
    "⛲",
    "⛱️",
  ],
  symbols: [
    "⭐",
    "🌟",
    "✨",
    "💫",
    "⚡",
    "🔥",
    "💥",
    "☄️",
    "🌈",
    "☀️",
    "🌙",
    "💧",
    "🌊",
    "💨",
    "🍀",
    "🔔",
    "🎵",
    "🎶",
    "💡",
    "🔑",
    "🗝️",
    "🔒",
    "🔓",
    "📌",
    "📍",
    "✅",
    "❌",
    "❓",
    "❗",
    "💯",
    "🔢",
    "🔤",
    "🅰️",
    "🅱️",
    "🆎",
    "🅾️",
    "🆘",
    "⛔",
    "🚫",
    "❤️",
    "🧡",
    "💛",
    "💚",
    "💙",
    "💜",
    "🖤",
    "🤍",
    "🤎",
    "♠️",
    "♣️",
    "♥️",
    "♦️",
    "🔴",
    "🟠",
    "🟡",
    "🟢",
    "🔵",
    "🟣",
    "⚫",
    "⚪",
  ],
};

const currentEmojis = computed(() => {
  return emojiData[activeCategory.value] || emojiData.smileys;
});

const selectEmoji = (emoji: string) => {
  emit("select", emoji);
  popoverRef.value?.hide();
};

// Expose toggle method for parent to call
const toggle = (event: Event) => {
  popoverRef.value?.toggle(event);
};

const hide = () => {
  popoverRef.value?.hide();
};

defineExpose({ toggle, hide });
</script>

<style scoped>
.emoji-picker-content {
  width: 280px;
  max-height: 320px;
  display: flex;
  flex-direction: column;
}

.emoji-categories {
  display: flex;
  gap: 2px;
  padding: 8px 8px 6px;
  border-bottom: 1px solid var(--surface-200);
  flex-wrap: wrap;
}

.category-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.category-btn:hover {
  background: var(--surface-100);
}

.category-btn.active {
  background: var(--primary-100);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  padding: 8px;
  overflow-y: auto;
  max-height: 260px;
}

.emoji-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.emoji-btn:hover {
  background: var(--surface-100);
  transform: scale(1.15);
}
</style>

<style>
/* Unscoped styles for popover positioning */
.inline-emoji-popover {
  /* Must be higher than PrimeVue Drawer (z-index ~1100) */
  z-index: 2000 !important;
  /* Ensure it doesn't overflow right edge */
  max-width: 290px;
}

.inline-emoji-popover .p-popover-content {
  padding: 0 !important;
}

/* Position popover to stay centered within drawer bounds */
.inline-emoji-popover.p-popover {
  /* Center within drawer (drawer is 460px, content ~420px after padding) */
  left: 50% !important;
  right: auto !important;
  transform: translateX(-50%) !important;
}

/* Hide arrow since positioning is customized */
.inline-emoji-popover::before,
.inline-emoji-popover::after {
  display: none !important;
}
</style>
