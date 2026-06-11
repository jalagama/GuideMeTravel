import { GuideMeLogger, LogEntry, LogLevel, truncateForLog } from "./GuideMeLogger";

export class InMemoryGuideMeLogger implements GuideMeLogger {
  readonly entries: LogEntry[] = [];

  private record(level: LogLevel, event: string, data: Record<string, unknown> = {}): void {
    this.entries.push({
      level,
      event,
      timestamp: Date.now(),
      data: { ...data },
    });
  }

  debug(event: string, data: Record<string, unknown> = {}): void {
    this.record("debug", event, data);
  }

  info(event: string, data: Record<string, unknown> = {}): void {
    this.record("info", event, data);
  }

  warn(event: string, data: Record<string, unknown> = {}): void {
    this.record("warn", event, data);
  }

  error(event: string, data: Record<string, unknown> = {}): void {
    this.record("error", event, data);
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

  clear(): void {
    this.entries.length = 0;
  }

  eventsNamed(event: string): LogEntry[] {
    return this.entries.filter((entry) => entry.event === event);
  }
}
