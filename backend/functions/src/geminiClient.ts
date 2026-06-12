import { type GeminiModelTier, geminiModelsToTry } from "./geminiConfig";
import { extractJsonArray } from "./mapsHelpers";
import { getGuideMeLogger } from "./logging/loggerContext";
import { fetchWithTimeout } from "./utils";

const GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * Google issues two API key formats:
 *  - Legacy "standard" keys: AIza...
 *  - New "auth" keys (default since 2026, often service-account bound): AQ....
 * Both are valid. They must be sent via the `x-goog-api-key` header (not the
 * legacy `?key=` query param, which rejects AQ. keys with API_KEY_INVALID).
 */
export function looksLikeGeminiApiKey(key: string): boolean {
  const trimmed = key.trim();
  return /^AIza[\w-]{10,}$/.test(trimmed) || /^AQ\.[\w.-]{10,}$/.test(trimmed);
}

export function requireGeminiApiKey(): string {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is not available in the function runtime");
  }
  if (!looksLikeGeminiApiKey(apiKey)) {
    throw new Error(
      "GEMINI_API_KEY is not a recognized Google AI Studio key (expected AIza... or AQ...). " +
        "Create one at https://aistudio.google.com/apikey. Do not use Maps keys, and do not " +
        "concatenate the key with itself."
    );
  }
  return apiKey;
}

export function hasGeminiApiKey(): boolean {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  return Boolean(apiKey && looksLikeGeminiApiKey(apiKey));
}

export function isInvalidGeminiApiKeyError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return (
    message.includes("API_KEY_INVALID") ||
    message.includes("API key not valid") ||
    message.includes("API key expired") ||
    message.includes("PERMISSION_DENIED")
  );
}

/** Billing/quota failures (depleted credits, exhausted quota) fail identically across models. */
export function isGeminiBillingError(error: unknown): boolean {
  const message = (error instanceof Error ? error.message : String(error)).toLowerCase();
  return (
    message.includes("prepayment credits") ||
    message.includes("credits are depleted") ||
    message.includes("billing") ||
    message.includes("resource_exhausted") ||
    message.includes("quota")
  );
}

/** Errors that will recur identically on every model, so retrying other models is pointless. */
export function isNonRetriableGeminiError(error: unknown): boolean {
  return isInvalidGeminiApiKeyError(error) || isGeminiBillingError(error);
}

type GeminiGenerationConfig = {
  responseMimeType?: string;
  temperature?: number;
};

/** Low-level REST call to Gemini generateContent using header-based auth. */
async function callGeminiRest(
  modelName: string,
  prompt: string,
  apiKey: string,
  generationConfig: GeminiGenerationConfig
): Promise<string> {
  const url = `${GEMINI_API_BASE}/${modelName}:generateContent`;
  const response = await fetchWithTimeout(
    url,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig,
      }),
    },
    20000,
    1
  );

  const data = (await response.json()) as {
    candidates?: Array<{
      content?: { parts?: Array<{ text?: string }> };
      finishReason?: string;
    }>;
    promptFeedback?: { blockReason?: string };
    error?: { message?: string; status?: string };
  };

  if (!response.ok || data.error) {
    const detail = data.error?.message || `HTTP ${response.status}`;
    throw new Error(`Gemini request failed (${modelName}): ${detail}`);
  }

  const text = (data.candidates?.[0]?.content?.parts ?? [])
    .map((part) => part.text ?? "")
    .join("")
    .trim();

  if (!text) {
    const blockReason = data.promptFeedback?.blockReason;
    throw new Error(
      blockReason
        ? `Gemini blocked the response (${modelName}): ${blockReason}`
        : `Gemini returned an empty response (${modelName})`
    );
  }

  return text;
}

/** Generate text, trying each configured model in order. Logs prompt/response. */
async function generateWithFallback(
  operation: string,
  prompt: string,
  context: Record<string, unknown>,
  generationConfig: GeminiGenerationConfig,
  tier: GeminiModelTier = "lite",
  primaryModel?: string
): Promise<string> {
  const apiKey = requireGeminiApiKey();
  const logger = getGuideMeLogger();
  let lastError: unknown;

  for (const modelName of geminiModelsToTry(tier, primaryModel)) {
    try {
      logger.logPrompt(operation, prompt, { ...context, modelName });
      const text = await callGeminiRest(modelName, prompt, apiKey, generationConfig);
      logger.logLlmResponse(operation, text, { ...context, modelName });
      return text;
    } catch (error) {
      lastError = error;
      logger.error("gemini_model_attempt_failed", {
        operation,
        modelName,
        error: error instanceof Error ? error.message : String(error),
        ...context,
      });
      // Invalid-key and billing/quota errors recur identically for every model — stop retrying.
      if (isNonRetriableGeminiError(error)) {
        break;
      }
    }
  }

  if (lastError instanceof Error) {
    throw lastError;
  }
  throw new Error("All Gemini model attempts failed");
}

export type GeminiCallOptions = {
  tier?: GeminiModelTier;
  /** When set, tries this model first before tier fallbacks. */
  model?: string;
};

export async function generateGeminiJson<T>(
  operation: string,
  prompt: string,
  context: Record<string, unknown> = {},
  options: GeminiCallOptions = {}
): Promise<T> {
  const text = await generateWithFallback(
    operation,
    prompt,
    context,
    {
      responseMimeType: "application/json",
      temperature: 0.2,
    },
    options.tier ?? "lite",
    options.model
  );
  return JSON.parse(extractJsonArray(text)) as T;
}

export async function generateGeminiPlainText(
  operation: string,
  prompt: string,
  context: Record<string, unknown> = {},
  options: GeminiCallOptions = {}
): Promise<string> {
  return generateWithFallback(
    operation,
    prompt,
    context,
    { temperature: 0.4 },
    options.tier ?? "lite",
    options.model
  );
}
