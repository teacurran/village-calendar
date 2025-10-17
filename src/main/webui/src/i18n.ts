import { createI18n } from "vue-i18n";
import en from "./i18n/en.json";
import fr from "./i18n/fr.json";
import es from "./i18n/es.json";

type MessageSchema = typeof en;

export default createI18n<[MessageSchema], "en" | "fr" | "es">({
  legacy: false,
  locale: "en",
  messages: {
    en: en,
    fr: fr,
    es: es,
  },
});
