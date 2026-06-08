import type { SupportedLanguageCode } from "./utils";

type VoiceConfig = {
  languageCode: string;
  name?: string;
  ssmlGender: "NEUTRAL" | "FEMALE" | "MALE";
};

const VOICE_MAP: Record<SupportedLanguageCode, VoiceConfig> = {
  en: { languageCode: "en-US", ssmlGender: "NEUTRAL" },
  hi: { languageCode: "hi-IN", ssmlGender: "FEMALE" },
  es: { languageCode: "es-ES", ssmlGender: "FEMALE" },
  fr: { languageCode: "fr-FR", ssmlGender: "FEMALE" },
  de: { languageCode: "de-DE", ssmlGender: "NEUTRAL" },
  zh: { languageCode: "cmn-CN", ssmlGender: "FEMALE" },
  ja: { languageCode: "ja-JP", ssmlGender: "FEMALE" },
  ar: { languageCode: "ar-XA", ssmlGender: "MALE" },
  pt: { languageCode: "pt-BR", ssmlGender: "FEMALE" },
  ru: { languageCode: "ru-RU", ssmlGender: "FEMALE" },
  it: { languageCode: "it-IT", ssmlGender: "FEMALE" },
  ko: { languageCode: "ko-KR", ssmlGender: "FEMALE" },
  bn: { languageCode: "bn-IN", ssmlGender: "FEMALE" },
  ta: { languageCode: "ta-IN", ssmlGender: "FEMALE" },
  te: { languageCode: "te-IN", ssmlGender: "FEMALE" },
};

export function getVoiceForLanguage(languageCode: SupportedLanguageCode): VoiceConfig {
  return VOICE_MAP[languageCode] ?? VOICE_MAP.en;
}
