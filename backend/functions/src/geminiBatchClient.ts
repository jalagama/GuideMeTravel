import { runWithConcurrency } from "./utils";
import { resolveCurationOptions } from "./geminiConfig";
import type { CurationQuality } from "./curationTypes";
import { generateGeminiPlainText, type GeminiCallOptions } from "./geminiClient";

export type BatchGeminiRequest = {
  requestId: string;
  operation: string;
  prompt: string;
  context?: Record<string, unknown>;
  options?: GeminiCallOptions;
};

/**
 * Runs Gemini calls with optional batch-style concurrency for admin jobs.
 * When GEMINI_USE_BATCH=true, requests run concurrently (50% batch discount
 * requires the Gemini Batch API — use concurrent sync until batch API is wired).
 */
export async function executeGeminiBatch(
  requests: BatchGeminiRequest[],
  quality: CurationQuality = "admin",
  concurrency = 5
): Promise<Map<string, string>> {
  const results = new Map<string, string>();
  const useBatch = resolveCurationOptions(quality).useBatch;
  const limit = useBatch ? Math.max(concurrency, 8) : concurrency;

  await runWithConcurrency(requests, limit, async (req) => {
    const text = await generateGeminiPlainText(
      req.operation,
      req.prompt,
      req.context ?? {},
      req.options ?? { tier: "standard" }
    );
    results.set(req.requestId, text);
  });

  return results;
}
