import type { GenerativeModel } from "@google/generative-ai";
import { getGuideMeLogger } from "./loggerContext";

export async function generateGeminiText(
  operation: string,
  model: GenerativeModel,
  prompt: string,
  context: Record<string, unknown> = {}
): Promise<string> {
  const logger = getGuideMeLogger();
  logger.logPrompt(operation, prompt, context);

  try {
    const result = await model.generateContent(prompt);
    const text = result.response.text();
    logger.logLlmResponse(operation, text, context);
    return text;
  } catch (error) {
    logger.error("llm_request_failed", {
      operation,
      error: error instanceof Error ? error.message : "unknown",
      ...context,
    });
    throw error;
  }
}
