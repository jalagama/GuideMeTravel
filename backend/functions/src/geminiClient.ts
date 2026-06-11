import { GoogleGenerativeAI } from "@google/generative-ai";
import { geminiModelsToTry } from "./geminiConfig";
import { extractJsonArray } from "./mapsHelpers";
import { getGuideMeLogger } from "./logging/loggerContext";

export function requireGeminiApiKey(): string {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is not available in the function runtime");
  }
  return apiKey;
}

export function hasGeminiApiKey(): boolean {
  return Boolean(process.env.GEMINI_API_KEY?.trim());
}

export async function generateGeminiJson<T>(
  operation: string,
  prompt: string,
  context: Record<string, unknown> = {}
): Promise<T> {
  const apiKey = requireGeminiApiKey();
  const logger = getGuideMeLogger();
  const genAI = new GoogleGenerativeAI(apiKey);
  let lastError: unknown;

  for (const modelName of geminiModelsToTry()) {
    try {
      const model = genAI.getGenerativeModel({
        model: modelName,
        generationConfig: {
          responseMimeType: "application/json",
          temperature: 0.2,
        },
      });

      logger.logPrompt(operation, prompt, { ...context, modelName });
      const result = await model.generateContent(prompt);
      const response = result.response;
      const text = response.text().trim();

      if (!text) {
        const blockReason = response.promptFeedback?.blockReason;
        throw new Error(
          blockReason
            ? `Gemini blocked the response (${modelName}): ${blockReason}`
            : `Gemini returned an empty response (${modelName})`
        );
      }

      logger.logLlmResponse(operation, text, { ...context, modelName });
      return JSON.parse(extractJsonArray(text)) as T;
    } catch (error) {
      lastError = error;
      logger.error("gemini_model_attempt_failed", {
        operation,
        modelName,
        error: error instanceof Error ? error.message : String(error),
        ...context,
      });
    }
  }

  if (lastError instanceof Error) {
    throw lastError;
  }
  throw new Error("All Gemini model attempts failed");
}
