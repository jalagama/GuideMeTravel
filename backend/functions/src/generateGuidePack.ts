import { GoogleGenerativeAI } from "@google/generative-ai";
import textToSpeech from "@google-cloud/text-to-speech";
import { Translate } from "@google-cloud/translate/build/src/v2";
import { HttpsError } from "firebase-functions/v2/https";
import { bucket, db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import { getVoiceForLanguage } from "./ttsVoices";
import {
  logEvent,
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

  logEvent("guide_pack_generated", {
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
    const data = cached.data()!;
    const storagePath = String(data.storagePath ?? "");
    if (!storagePath) {
      throw new HttpsError(
        "failed-precondition",
        `Cached guide for ${attraction.id} is missing storagePath`
      );
    }
    return {
      attractionId: attraction.id,
      url: await createSignedUrl(storagePath),
      storagePath,
      transcript: data.transcript,
    };
  }

  const transcript = await buildGuideScript(attraction, languageCode);
  const storagePath = `guide-audio/${attraction.id}/${languageCode}/guide.mp3`;
  await synthesizeAndUpload(storagePath, languageCode, transcript);

  await db
    .collection("attractions")
    .doc(attraction.id)
    .collection("guideContent")
    .doc(languageCode)
    .set({
      transcript,
      storagePath,
      updatedAtMillis: Date.now(),
      source: "gemini+wikipedia",
    });

  return {
    attractionId: attraction.id,
    url: await createSignedUrl(storagePath),
    storagePath,
    transcript,
  };
}

async function buildGuideScript(
  attraction: Attraction,
  languageCode: SupportedLanguageCode
): Promise<string> {
  const wikiFacts = await fetchWikipediaSummary(attraction.name, languageCode);
  const groundedFacts = wikiFacts || `${attraction.name}. ${attraction.description}`;
  const apiKey = process.env.GEMINI_API_KEY;

  let script = `Welcome to ${attraction.name}. ${groundedFacts}`;

  if (apiKey) {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
    const prompt = `
Write a 90-second engaging audio guide script for a tourist arriving at ${attraction.name}.
Use only these facts:
${groundedFacts}
Keep it conversational and factual. Do not invent details.
`;
    const result = await model.generateContent(prompt);
    script = result.response.text().trim();
  }

  if (languageCode !== "en") {
    const [translation] = await translateClient.translate(script, languageCode);
    script = translation;
  }

  return script;
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
