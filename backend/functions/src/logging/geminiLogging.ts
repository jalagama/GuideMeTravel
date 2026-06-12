import { generateGeminiPlainText, type GeminiCallOptions } from "../geminiClient";

/**
 * Generate plain text from Gemini. Routes through the REST client so both
 * AIza and AQ. API key formats work, with automatic model fallback.
 */
export async function generateGeminiText(
  operation: string,
  prompt: string,
  context: Record<string, unknown> = {},
  options: GeminiCallOptions = {}
): Promise<string> {
  return generateGeminiPlainText(operation, prompt, context, options);
}
