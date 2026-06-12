import type { SupportedLanguageCode } from "./utils";
import { getTtsVoiceTier } from "./geminiConfig";

type VoiceConfig = {
  languageCode: string;
  name?: string;
  ssmlGender: "NEUTRAL" | "FEMALE" | "MALE";
};

const WAVENET_VOICES: Record<SupportedLanguageCode, VoiceConfig> = {
  en: { languageCode: "en-US", name: "en-US-Wavenet-D", ssmlGender: "NEUTRAL" },
  hi: { languageCode: "hi-IN", name: "hi-IN-Wavenet-A", ssmlGender: "FEMALE" },
  es: { languageCode: "es-ES", name: "es-ES-Wavenet-B", ssmlGender: "FEMALE" },
  fr: { languageCode: "fr-FR", name: "fr-FR-Wavenet-A", ssmlGender: "FEMALE" },
  de: { languageCode: "de-DE", name: "de-DE-Wavenet-F", ssmlGender: "NEUTRAL" },
  zh: { languageCode: "cmn-CN", name: "cmn-CN-Wavenet-A", ssmlGender: "FEMALE" },
  ja: { languageCode: "ja-JP", name: "ja-JP-Wavenet-A", ssmlGender: "FEMALE" },
  ar: { languageCode: "ar-XA", name: "ar-XA-Wavenet-A", ssmlGender: "MALE" },
  pt: { languageCode: "pt-BR", name: "pt-BR-Wavenet-A", ssmlGender: "FEMALE" },
  ru: { languageCode: "ru-RU", name: "ru-RU-Wavenet-A", ssmlGender: "FEMALE" },
  it: { languageCode: "it-IT", name: "it-IT-Wavenet-A", ssmlGender: "FEMALE" },
  ko: { languageCode: "ko-KR", name: "ko-KR-Wavenet-A", ssmlGender: "FEMALE" },
  bn: { languageCode: "bn-IN", name: "bn-IN-Wavenet-A", ssmlGender: "FEMALE" },
  ta: { languageCode: "ta-IN", name: "ta-IN-Wavenet-A", ssmlGender: "FEMALE" },
  te: { languageCode: "te-IN", name: "te-IN-Wavenet-A", ssmlGender: "FEMALE" },
};

export function getVoiceForLanguage(languageCode: SupportedLanguageCode): VoiceConfig {
  const tier = getTtsVoiceTier();
  const voice = WAVENET_VOICES[languageCode] ?? WAVENET_VOICES.en;
  if (tier === "wavenet") {
    return voice;
  }
  return { languageCode: voice.languageCode, ssmlGender: voice.ssmlGender };
}
