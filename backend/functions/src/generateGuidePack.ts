import { GoogleGenerativeAI } from "@google/generative-ai";
import textToSpeech from "@google-cloud/text-to-speech";
import { Translate } from "@google-cloud/translate/build/src/v2";
import { bucket, db } from "./firebaseAdmin";
const ttsClient = new textToSpeech.TextToSpeechClient();
const translateClient = new Translate();

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

export async function generateGuidePackForTrip(input: GenerateGuidePackInput) {
  const tripSnap = await db.collection("trips").doc(input.tripId).get();
  if (!tripSnap.exists) {
    throw new Error("Trip not found");
  }

  const trip = tripSnap.data()!;
  if (trip.userId !== input.userId) {
    throw new Error("Unauthorized trip access");
  }

  const attractions = (trip.attractions ?? []) as Attraction[];
  const languageCode = String(trip.languageCode ?? "en");
  const audioFiles: Array<{ attractionId: string; url: string; transcript: string }> = [];

  for (const attraction of attractions) {
    const cached = await db
      .collection("attractions")
      .doc(attraction.id)
      .collection("guideContent")
      .doc(languageCode)
      .get();

    if (cached.exists) {
      const data = cached.data()!;
      audioFiles.push({
        attractionId: attraction.id,
        url: data.audioUrl,
        transcript: data.transcript,
      });
      continue;
    }

    const transcript = await buildGuideScript(attraction, languageCode);
    const audioUrl = await synthesizeAndUpload(attraction.id, languageCode, transcript);

    await db
      .collection("attractions")
      .doc(attraction.id)
      .collection("guideContent")
      .doc(languageCode)
      .set({
        transcript,
        audioUrl,
        updatedAtMillis: Date.now(),
        source: "gemini+wikipedia",
      });

    audioFiles.push({
      attractionId: attraction.id,
      url: audioUrl,
      transcript,
    });
  }

  await db.collection("trips").doc(input.tripId).update({
    offlinePackDownloaded: true,
    status: "READY",
    guidePackGeneratedAtMillis: Date.now(),
  });

  return {
    tripId: input.tripId,
    languageCode,
    audioFiles,
  };
}

async function buildGuideScript(attraction: Attraction, languageCode: string): Promise<string> {
  const wikiFacts = await fetchWikipediaSummary(attraction.name);
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

async function fetchWikipediaSummary(title: string): Promise<string> {
  const url = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}`;
  const response = await fetch(url);
  if (!response.ok) return "";
  const data = await response.json();
  return typeof data.extract === "string" ? data.extract : "";
}

async function synthesizeAndUpload(
  poiId: string,
  languageCode: string,
  transcript: string
): Promise<string> {
  const [response] = await ttsClient.synthesizeSpeech({
    input: { text: transcript },
    voice: { languageCode, ssmlGender: "NEUTRAL" },
    audioConfig: { audioEncoding: "MP3" },
  });

  const filePath = `guide-audio/${poiId}/${languageCode}/guide.mp3`;
  const file = bucket.file(filePath);
  await file.save(response.audioContent as Buffer, {
    contentType: "audio/mpeg",
    metadata: { cacheControl: "public,max-age=31536000" },
  });

  const [signedUrl] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + 1000 * 60 * 60 * 24 * 7,
  });

  return signedUrl;
}
