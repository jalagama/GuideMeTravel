import assert from "node:assert/strict";
import test from "node:test";
import { InMemoryGuideMeLogger } from "./InMemoryGuideMeLogger";

test("InMemoryGuideMeLogger records prompt and response", () => {
  const logger = new InMemoryGuideMeLogger();
  logger.logPrompt("genres", "prompt body", { countryCode: "IN" });
  logger.logLlmResponse("genres", '{"genres":[]}', { countryCode: "IN" });

  assert.equal(logger.eventsNamed("llm_prompt").length, 1);
  assert.equal(logger.eventsNamed("llm_response").length, 1);
  assert.equal(logger.eventsNamed("llm_prompt")[0].data.operation, "genres");
  assert.equal(logger.eventsNamed("llm_prompt")[0].data.countryCode, "IN");
});

test("InMemoryGuideMeLogger truncates long prompt text", () => {
  const logger = new InMemoryGuideMeLogger();
  const longPrompt = "x".repeat(5000);
  logger.logPrompt("packages", longPrompt);

  const prompt = String(logger.eventsNamed("llm_prompt")[0].data.prompt);
  assert.ok(prompt.includes("truncated"));
  assert.equal(logger.eventsNamed("llm_prompt")[0].data.promptLength, 5000);
});

test("InMemoryGuideMeLogger records cache and validation events", () => {
  const logger = new InMemoryGuideMeLogger();
  logger.logCacheHit("curatedGenres", "IN");
  logger.logCacheMiss("curatedPackages", "IN_hill-stations");
  logger.logValidationFailure("package_extras", "overview too short", { packageId: "in-goa" });
  logger.logFallback("genres", "missing api key", { countryCode: "IN" });

  assert.equal(logger.eventsNamed("cache_hit").length, 1);
  assert.equal(logger.eventsNamed("cache_miss").length, 1);
  assert.equal(logger.eventsNamed("validation_failed").length, 1);
  assert.equal(logger.eventsNamed("fallback_used").length, 1);
});
