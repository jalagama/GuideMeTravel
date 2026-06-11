/**
 * Central Gemini model configuration.
 *
 * gemini-2.0-flash was retired on 2026-06-01 (404). We call Gemini via REST
 * (see geminiClient.ts) with header auth so both AIza and AQ. keys work.
 * Override the model with the GEMINI_MODEL env var if needed.
 */
export const GEMINI_MODEL_FALLBACKS = [
  "gemini-2.5-flash",
  "gemini-2.5-flash-lite",
  "gemini-2.5-pro",
] as const;

export const GEMINI_MODEL =
  process.env.GEMINI_MODEL?.trim() || GEMINI_MODEL_FALLBACKS[0];

export function geminiModelsToTry(): string[] {
  const primary = GEMINI_MODEL;
  return [primary, ...GEMINI_MODEL_FALLBACKS.filter((model) => model !== primary)];
}
