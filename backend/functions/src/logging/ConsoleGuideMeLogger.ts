import { logEvent } from "../utils";
import { GuideMeLogger, LogLevel, truncateForLog } from "./GuideMeLogger";

export class ConsoleGuideMeLogger implements GuideMeLogger {
  private emit(level: LogLevel, event: string, data: Record<string, unknown> = {}): void {
    logEvent(event, { level, ...data });
  }

  debug(event: string, data: Record<string, unknown> = {}): void {
    this.emit("debug", event, data);
  }

  info(event: string, data: Record<string, unknown> = {}): void {
    this.emit("info", event, data);
  }

  warn(event: string, data: Record<string, unknown> = {}): void {
    this.emit("warn", event, data);
  }

  error(event: string, data: Record<string, unknown> = {}): void {
    this.emit("error", event, data);
  }

  logCacheHit(scope: string, key: string, data: Record<string, unknown> = {}): void {
    this.info("cache_hit", { scope, key, ...data });
  }

  logCacheMiss(scope: string, key: string, data: Record<string, unknown> = {}): void {
    this.info("cache_miss", { scope, key, ...data });
  }

  logPrompt(operation: string, prompt: string, data: Record<string, unknown> = {}): void {
    this.debug("llm_prompt", {
      operation,
      prompt: truncateForLog(prompt),
      promptLength: prompt.length,
      ...data,
    });
  }

  logLlmResponse(operation: string, response: string, data: Record<string, unknown> = {}): void {
    this.debug("llm_response", {
      operation,
      response: truncateForLog(response),
      responseLength: response.length,
      ...data,
    });
  }

  logValidationFailure(
    operation: string,
    reason: string,
    data: Record<string, unknown> = {}
  ): void {
    this.warn("validation_failed", { operation, reason, ...data });
  }

  logFallback(operation: string, reason: string, data: Record<string, unknown> = {}): void {
    this.warn("fallback_used", { operation, reason, ...data });
  }
}
