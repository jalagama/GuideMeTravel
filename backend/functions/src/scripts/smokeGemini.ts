/**
 * Quick local check: GEMINI_API_KEY + model list work before deploy.
 *
 * Usage:
 *   GEMINI_API_KEY=your_key npm run smoke:gemini
 */
import { generateGeminiJson } from "../geminiClient";
import { buildSpotsPrompt } from "../prompts/curatedPrompts";
import { geminiModelsToTry } from "../geminiConfig";

async function main(): Promise<void> {
  if (!process.env.GEMINI_API_KEY?.trim()) {
    console.error("Set GEMINI_API_KEY in the environment first.");
    process.exit(1);
  }

  console.log("Models to try:", geminiModelsToTry().join(", "));

  const prompt = buildSpotsPrompt("Hampi", "Karnataka", "India", "IN", 5);
  const spots = await generateGeminiJson<Array<{ name: string; rank?: number }>>(
    "smoke_expert_spots",
    prompt,
    { destination: "Hampi" }
  );

  if (!Array.isArray(spots) || spots.length === 0) {
    console.error("Gemini returned no spots.");
    process.exit(1);
  }

  console.log("OK — sample spots:");
  for (const spot of spots.slice(0, 5)) {
    console.log(`  ${spot.rank ?? "?"}. ${spot.name}`);
  }
}

main().catch((error) => {
  console.error("Smoke test failed:", error instanceof Error ? error.message : error);
  process.exit(1);
});
