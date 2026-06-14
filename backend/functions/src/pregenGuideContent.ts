import textToSpeech from "@google-cloud/text-to-speech";
import { Translate } from "@google-cloud/translate/build/src/v2";
import { bucket, db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import { getVoiceForLanguage } from "./ttsVoices";
import { generateGeminiText } from "./logging/geminiLogging";
import { getGuideMeLogger } from "./logging/loggerContext";
import { buildAttractionGuideScriptPrompt } from "./prompts/curatedPrompts";
import { hasGeminiApiKey } from "./geminiClient";
import { resolveCurationOptions } from "./geminiConfig";
import type { CurationContext } from "./curationTypes";
import {
  runWithConcurrency,
  validateLanguageCode,
  type SupportedLanguageCode,
} from "./utils";

const ttsClient = new textToSpeech.TextToSpeechClient();
const translateClient = new Translate();
const GUIDE_PREGEN_CONCURRENCY = 3;
const GEO_TRIGGER_RADIUS_METERS = 400;
const MIN_FULL_NARRATION_WORDS = 150;

export type GuideAttractionInput = {
  id: string;
  name: string;
  description: string;
  transcript?: string;
  latitude?: number;
  longitude?: number;
};

export async function pregenGuideContentForSpot(
  spot: GuideAttractionInput,
  languageCode: SupportedLanguageCode,
  context: CurationContext = { quality: "admin" }
): Promise<void> {
  const cached = await db
    .collection("attractions")
    .doc(spot.id)
    .collection("guideContent")
    .doc(languageCode)
    .get();

  if (cached.exists && cached.data()?.audioAvailable) {
    return;
  }

  const { transcript, source } = await resolveGuideTranscript(spot, languageCode, context);
  const storagePath = `guide-audio/${spot.id}/${languageCode}/guide.mp3`;

  await db.collection("attractions").doc(spot.id).set(
    {
      name: spot.name,
      description: spot.description,
      updatedAtMillis: Date.now(),
    },
    { merge: true }
  );

  let audioAvailable = false;
  let savedPath: string | null = null;
  try {
    await synthesizeAndUpload(storagePath, languageCode, transcript);
    audioAvailable = true;
    savedPath = storagePath;
  } catch (error) {
    getGuideMeLogger().error("pregen_tts_failed", {
      spotId: spot.id,
      languageCode,
      error: error instanceof Error ? error.message : "unknown",
    });
  }

  await db
    .collection("attractions")
    .doc(spot.id)
    .collection("guideContent")
    .doc(languageCode)
    .set({
      transcript,
      storagePath: savedPath,
      audioAvailable,
      updatedAtMillis: Date.now(),
      source,
      geoTrigger: buildGeoTriggerMetadata(spot),
    });
}

export async function pregenGuideContentBatch(
  spots: GuideAttractionInput[],
  languageCode: SupportedLanguageCode,
  context: CurationContext = { quality: "admin" }
): Promise<number> {
  let done = 0;
  await runWithConcurrency(spots, GUIDE_PREGEN_CONCURRENCY, async (spot) => {
    await pregenGuideContentForSpot(spot, languageCode, context);
    done += 1;
  });
  return done;
}

async function resolveGuideTranscript(
  attraction: GuideAttractionInput,
  languageCode: SupportedLanguageCode,
  context: CurationContext
): Promise<{ transcript: string; source: string }> {
  const existing = attraction.transcript?.trim();
  if (existing && existing.split(/\s+/).filter(Boolean).length >= MIN_FULL_NARRATION_WORDS) {
    if (languageCode === "en") {
      return { transcript: existing, source: "package" };
    }
    try {
      const [translation] = await translateClient.translate(existing, languageCode);
      return { transcript: translation, source: "package+translate" };
    } catch {
      return { transcript: existing, source: "package" };
    }
  }

  const { script, source } = await buildGuideScript(attraction, languageCode, context);
  return { transcript: script, source };
}

async function buildGuideScript(
  attraction: GuideAttractionInput,
  languageCode: SupportedLanguageCode,
  context: CurationContext
): Promise<{ script: string; source: string }> {
  const wikiFacts = await fetchWikipediaSummary(attraction.name, languageCode);
  const groundedFacts = wikiFacts || `${attraction.name}. ${attraction.description}`;

  let script = `Welcome to ${attraction.name}. ${groundedFacts}`;
  let source = "wikipedia";

  if (hasGeminiApiKey()) {
    try {
      const prompt = buildAttractionGuideScriptPrompt(
        attraction.name,
        groundedFacts,
        languageCode
      );
      const adminOpts = resolveCurationOptions(context.quality);
      script = (
        await generateGeminiText(
          "build_guide_script",
          prompt,
          { attraction: attraction.name, languageCode },
          { tier: adminOpts.tier, model: adminOpts.model }
        )
      ).trim();
      source = "gemini+wikipedia";
    } catch (error) {
      getGuideMeLogger().error("gemini_script_failed", {
        attraction: attraction.name,
        error: error instanceof Error ? error.message : "unknown",
      });
    }
  }

  if (languageCode !== "en") {
    try {
      const [translation] = await translateClient.translate(script, languageCode);
      script = translation;
      source = `${source}+translate`;
    } catch (error) {
      getGuideMeLogger().error("translate_failed", {
        attraction: attraction.name,
        languageCode,
        error: error instanceof Error ? error.message : "unknown",
      });
    }
  }

  return { script, source };
}

function buildGeoTriggerMetadata(spot: GuideAttractionInput) {
  if (spot.latitude == null || spot.longitude == null) {
    return null;
  }
  return {
    latitude: spot.latitude,
    longitude: spot.longitude,
    triggerRadiusMeters: GEO_TRIGGER_RADIUS_METERS,
  };
}

async function synthesizeAndUpload(
  storagePath: string,
  languageCode: SupportedLanguageCode,
  transcript: string
): Promise<void> {
  const voice = getVoiceForLanguage(languageCode);
  const [response] = await ttsClient.synthesizeSpeech({
    input: { text: transcript },
    voice: {
      languageCode: voice.languageCode,
      ssmlGender: voice.ssmlGender,
      name: voice.name,
    },
    audioConfig: { audioEncoding: "MP3" },
  });

  const file = bucket.file(storagePath);
  await file.save(response.audioContent as Buffer, {
    contentType: "audio/mpeg",
    metadata: { cacheControl: "public,max-age=31536000" },
  });
}

export async function collectSpotsForCountry(countryCode: string): Promise<GuideAttractionInput[]> {
  const normalized = countryCode.trim().toUpperCase();
  const prefix = `${normalized.toLowerCase()}-`;
  const packagesSnap = await db.collection("tourPackages").get();
  const spots = new Map<string, GuideAttractionInput>();

  for (const doc of packagesSnap.docs) {
    const data = doc.data();
    if (data.countryCode !== normalized && !doc.id.startsWith(prefix)) {
      continue;
    }
    const packageSpots = (data.spots ?? []) as Array<{
      id?: string;
      name?: string;
      description?: string;
      transcript?: string;
      latitude?: number;
      longitude?: number;
    }>;
    for (const spot of packageSpots) {
      const id = spot.id ?? doc.id;
      if (!id || spots.has(id)) continue;
      spots.set(id, {
        id,
        name: String(spot.name ?? id),
        description: String(spot.description ?? ""),
        transcript: spot.transcript,
        latitude: typeof spot.latitude === "number" ? spot.latitude : undefined,
        longitude: typeof spot.longitude === "number" ? spot.longitude : undefined,
      });
    }
  }
  return [...spots.values()];
}

export function validateLanguages(languages: string[]): SupportedLanguageCode[] {
  return languages.map((lang) => validateLanguageCode(lang));
}
