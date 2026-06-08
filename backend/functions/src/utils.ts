import { HttpsError } from "firebase-functions/v2/https";

export const SUPPORTED_LANGUAGE_CODES = [
  "en",
  "hi",
  "es",
  "fr",
  "de",
  "zh",
  "ja",
  "ar",
  "pt",
  "ru",
  "it",
  "ko",
  "bn",
  "ta",
  "te",
] as const;

export type SupportedLanguageCode = (typeof SUPPORTED_LANGUAGE_CODES)[number];

export function validateLanguageCode(languageCode: string): SupportedLanguageCode {
  const normalized = languageCode.trim().toLowerCase();
  if (!SUPPORTED_LANGUAGE_CODES.includes(normalized as SupportedLanguageCode)) {
    throw new HttpsError(
      "invalid-argument",
      `Unsupported languageCode: ${languageCode}. Supported: ${SUPPORTED_LANGUAGE_CODES.join(", ")}`
    );
  }
  return normalized as SupportedLanguageCode;
}

export function toHttpsError(error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof Error) {
    if (error.message === "Trip not found") {
      return new HttpsError("not-found", error.message);
    }
    if (error.message === "Unauthorized trip access") {
      return new HttpsError("permission-denied", error.message);
    }
    return new HttpsError("internal", error.message);
  }
  return new HttpsError("internal", "Unknown error");
}

export async function fetchWithTimeout(
  url: string,
  options: RequestInit = {},
  timeoutMs = 15000,
  retries = 2
): Promise<Response> {
  let lastError: Error | null = null;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, { ...options, signal: controller.signal });
      clearTimeout(timeout);
      return response;
    } catch (error) {
      clearTimeout(timeout);
      lastError = error instanceof Error ? error : new Error("Fetch failed");
      if (attempt < retries) {
        await new Promise((resolve) => setTimeout(resolve, 500 * (attempt + 1)));
      }
    }
  }
  throw new HttpsError(
    "unavailable",
    `Request failed after retries: ${lastError?.message ?? "unknown"}`
  );
}

export async function runWithConcurrency<T, R>(
  items: T[],
  concurrency: number,
  worker: (item: T) => Promise<R>
): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let index = 0;

  async function runWorker(): Promise<void> {
    while (index < items.length) {
      const current = index++;
      results[current] = await worker(items[current]);
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, items.length) }, () => runWorker());
  await Promise.all(workers);
  return results;
}

export function logEvent(event: string, data: Record<string, unknown>): void {
  console.log(JSON.stringify({ event, timestamp: Date.now(), ...data }));
}
