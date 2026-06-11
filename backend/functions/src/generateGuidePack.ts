import { GoogleGenerativeAI } from "@google/generative-ai";
import textToSpeech from "@google-cloud/text-to-speech";
import { Translate } from "@google-cloud/translate/build/src/v2";
import { HttpsError } from "firebase-functions/v2/https";
import { bucket, db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import { getVoiceForLanguage } from "./ttsVoices";
import { generateGeminiText } from "./logging/geminiLogging";
import { getGuideMeLogger } from "./logging/loggerContext";
import { buildAttractionGuideScriptPrompt } from "./prompts/curatedPrompts";
import { GEMINI_MODEL } from "./geminiConfig";
import {
  runWithConcurrency,
  validateLanguageCode,
  type SupportedLanguageCode,
} from "./utils";

const ttsClient = new textToSpeech.TextToSpeechClient();
const translateClient = new Translate();
const GUIDE_PACK_CONCURRENCY = 3;
const SIGNED_URL_TTL_MS = 1000 * 60 * 60;

type GenerateGuidePackInput = {
  userId: string;
  tripId: string;
};

type Attraction = {
  id: string;
  name: string;
  description: string;
  latitude: number;
  longitude: number;
  orderIndex: number;
  estimatedMinutes: number;
  transcript?: string;
};

type AudioFileResult = {
  attractionId: string;
  url: string;
  storagePath: string;
  transcript: string;
};

export async function generateGuidePackForTrip(input: GenerateGuidePackInput) {
  const tripSnap = await db.collection("trips").doc(input.tripId).get();
  if (!tripSnap.exists) {
    throw new HttpsError("not-found", "Trip not found");
  }

  const trip = tripSnap.data()!;
  if (trip.userId !== input.userId) {
    throw new HttpsError("permission-denied", "Unauthorized trip access");
  }

  const attractions = (trip.attractions ?? []) as Attraction[];
  const languageCode = validateLanguageCode(String(trip.languageCode ?? "en"));

  const audioFiles = await runWithConcurrency(
    attractions,
    GUIDE_PACK_CONCURRENCY,
    (attraction) => generateGuideForAttraction(attraction, languageCode)
  );

  await db.collection("trips").doc(input.tripId).update({
    offlinePackDownloaded: true,
    status: "READY",
    guidePackGeneratedAtMillis: Date.now(),
  });

  getGuideMeLogger().info("guide_pack_generated", {
    tripId: input.tripId,
    userId: input.userId,
    languageCode,
    attractionCount: attractions.length,
  });

  return {
    tripId: input.tripId,
    languageCode,
    audioFiles,
  };
}

async function generateGuideForAttraction(
  attraction: Attraction,
  languageCode: SupportedLanguageCode
): Promise<AudioFileResult> {
  const cached = await db
    .collection("attractions")
    .doc(attraction.id)
    .collection("guideContent")
    .doc(languageCode)
    .get();

  if (cached.exists) {
    getGuideMeLogger().logCacheHit("guideContent", `${attraction.id}/${languageCode}`);
    return resolveCachedGuide(attraction.id, cached.data()!);
  }

  getGuideMeLogger().logCacheMiss("guideContent", `${attraction.id}/${languageCode}`);

  const { transcript, source } = await resolveGuideTranscript(attraction, languageCode);
  const storagePath = `guide-audio/${attraction.id}/${languageCode}/guide.mp3`;
  const audio = await trySynthesizeAndUpload(storagePath, languageCode, transcript);

  await db
    .collection("attractions")
    .doc(attraction.id)
    .collection("guideContent")
    .doc(languageCode)
    .set({
      transcript,
      storagePath: audio.storagePath || null,
      audioAvailable: audio.audioAvailable,
      updatedAtMillis: Date.now(),
      source,
    });

  return {
    attractionId: attraction.id,
    url: audio.url,
    storagePath: audio.storagePath,
    transcript,
  };
}

async function resolveCachedGuide(
  attractionId: string,
  data: FirebaseFirestore.DocumentData
): Promise<AudioFileResult> {
  const transcript = String(data.transcript ?? "");
  const storagePath = String(data.storagePath ?? "");
  if (!storagePath) {
    return {
      attractionId,
      url: "",
      storagePath: "",
      transcript,
    };
  }

  return {
    attractionId,
    url: await createSignedUrl(storagePath),
    storagePath,
    transcript,
  };
}

async function resolveGuideTranscript(
  attraction: Attraction,
  languageCode: SupportedLanguageCode
): Promise<{ transcript: string; source: string }> {
  const existing = attraction.transcript?.trim();
  if (existing) {
    return { transcript: existing, source: "itinerary" };
  }

  const { script, source } = await buildGuideScript(attraction, languageCode);
  return { transcript: script, source };
}

async function buildGuideScript(
  attraction: Attraction,
  languageCode: SupportedLanguageCode
): Promise<{ script: string; source: string }> {
  const wikiFacts = await fetchWikipediaSummary(attraction.name, languageCode);
  const groundedFacts = wikiFacts || `${attraction.name}. ${attraction.description}`;
  const apiKey = process.env.GEMINI_API_KEY?.trim();

  let script = `Welcome to ${attraction.name}. ${groundedFacts}`;
  let source = "wikipedia";

  if (apiKey) {
    try {
      const genAI = new GoogleGenerativeAI(apiKey);
      const model = genAI.getGenerativeModel({ model: GEMINI_MODEL });
      const prompt = buildAttractionGuideScriptPrompt(
        attraction.name,
        groundedFacts,
        languageCode
      );
      script = (
        await generateGeminiText("build_guide_script", model, prompt, {
          attraction: attraction.name,
          languageCode,
        })
      ).trim();
      source = "gemini+wikipedia";
    } catch (error) {
      getGuideMeLogger().error("gemini_script_failed", {
        attraction: attraction.name,
        error: error instanceof Error ? error.message : "unknown",
      });
      getGuideMeLogger().logFallback("build_guide_script", "generation_failed", {
        attraction: attraction.name,
      });
    }
  }

  if (languageCode !== "en") {
    try {
      const [translation] = await translateClient.translate(script, languageCode);
      script = translation;
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

async function trySynthesizeAndUpload(
  storagePath: string,
  languageCode: SupportedLanguageCode,
  transcript: string
): Promise<{ url: string; storagePath: string; audioAvailable: boolean }> {
  try {
    await synthesizeAndUpload(storagePath, languageCode, transcript);
    return {
      url: await createSignedUrl(storagePath),
      storagePath,
      audioAvailable: true,
    };
  } catch (error) {
    getGuideMeLogger().error("tts_synthesis_failed", {
      storagePath,
      languageCode,
      error: error instanceof Error ? error.message : "unknown",
    });
    return {
      url: "",
      storagePath: "",
      audioAvailable: false,
    };
  }
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

export async function createSignedUrl(storagePath: string): Promise<string> {
  const file = bucket.file(storagePath);
  const [signedUrl] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + SIGNED_URL_TTL_MS,
  });
  return signedUrl;
}
