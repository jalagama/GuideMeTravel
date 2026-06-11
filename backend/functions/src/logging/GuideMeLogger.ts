export type LogLevel = "debug" | "info" | "warn" | "error";

export type LogEntry = {
  level: LogLevel;
  event: string;
  timestamp: number;
  data: Record<string, unknown>;
};

export interface GuideMeLogger {
  debug(event: string, data?: Record<string, unknown>): void;
  info(event: string, data?: Record<string, unknown>): void;
  warn(event: string, data?: Record<string, unknown>): void;
  error(event: string, data?: Record<string, unknown>): void;

  logCacheHit(scope: string, key: string, data?: Record<string, unknown>): void;
  logCacheMiss(scope: string, key: string, data?: Record<string, unknown>): void;
  logPrompt(operation: string, prompt: string, data?: Record<string, unknown>): void;
  logLlmResponse(operation: string, response: string, data?: Record<string, unknown>): void;
  logValidationFailure(operation: string, reason: string, data?: Record<string, unknown>): void;
  logFallback(operation: string, reason: string, data?: Record<string, unknown>): void;
}

export const MAX_LOG_TEXT_LENGTH = 4000;

export function truncateForLog(value: string, maxLength = MAX_LOG_TEXT_LENGTH): string {
  if (value.length <= maxLength) return value;
  return `${value.slice(0, maxLength)}…[truncated ${value.length - maxLength} chars]`;
}
