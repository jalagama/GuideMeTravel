/**
 * Central Gemini model configuration.
 *
 * gemini-2.0-flash was retired on 2026-06-01 and now returns 404, which caused
 * curation to silently fall back to Google Places noise. Keep the model name in
 * one place and allow an env override so future model retirements are a config
 * change rather than a code change.
 */
export const GEMINI_MODEL = process.env.GEMINI_MODEL?.trim() || "gemini-2.5-flash";
