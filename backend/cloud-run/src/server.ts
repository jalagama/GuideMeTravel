import express from "express";
import * as admin from "firebase-admin";
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { generateGuidePackForTrip, getGuidePackForTrip } = require("../../functions/lib/generateGuidePack");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { enforceRateLimit } = require("../../functions/lib/rateLimit");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { logEvent, toHttpsError } = require("../../functions/lib/utils");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { advanceCurationJob, startCountryCuration, getCurationJob } = require("../../functions/lib/bulkCurateCountry");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { requireAdminBearer } = require("../../functions/lib/admin/requireAdmin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const app = express();
app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.post("/generateGuidePack", async (req, res) => {
  try {
    const authHeader = req.headers.authorization ?? "";
    const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
    if (!token) {
      res.status(401).json({ error: "Missing bearer token" });
      return;
    }

    const decoded = await admin.auth().verifyIdToken(token);
    const tripId = String(req.body.tripId ?? "").trim();
    if (!tripId) {
      res.status(400).json({ error: "tripId is required" });
      return;
    }

    await enforceRateLimit({
      key: `guidepack:${decoded.uid}`,
      maxRequests: 5,
      windowMs: 60 * 60 * 1000,
    });

    const result = await generateGuidePackForTrip({
      userId: decoded.uid,
      tripId,
    });
    res.json(result);
  } catch (error) {
    const code = getCallableErrorCode(toHttpsError(error)) ?? getCallableErrorCode(error);
    const status = code ? mapHttpsErrorToStatus(code) : 500;
    const message = error instanceof Error ? error.message : "Unknown error";
    logEvent("generate_guide_pack_failed", { error: message, status });
    res.status(status).json({ error: message });
  }
});

app.post("/getGuidePackForTrip", async (req, res) => {
  try {
    const authHeader = req.headers.authorization ?? "";
    const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
    if (!token) {
      res.status(401).json({ error: "Missing bearer token" });
      return;
    }

    const decoded = await admin.auth().verifyIdToken(token);
    const tripId = String(req.body.tripId ?? "").trim();
    if (!tripId) {
      res.status(400).json({ error: "tripId is required" });
      return;
    }

    const result = await getGuidePackForTrip({ userId: decoded.uid, tripId });
    res.json(result);
  } catch (error) {
    const code = getCallableErrorCode(error);
    const status = code ? mapHttpsErrorToStatus(code) : 500;
    res.status(status).json({ error: error instanceof Error ? error.message : "Unknown error" });
  }
});

app.post("/admin/curation/start", async (req, res) => {
  try {
    const uid = await requireAdminBearer(req.headers.authorization ?? "");
    process.env.GEMINI_API_KEY = process.env.GEMINI_API_KEY ?? "";
    const countryCode = String(req.body.countryCode ?? "").trim();
    const mode = String(req.body.mode ?? "full");
    const languages = Array.isArray(req.body.languages) ? req.body.languages.map(String) : ["en"];
    const result = await startCountryCuration({ countryCode, languages, mode, startedBy: uid });
    res.json(result);
  } catch (error) {
    const code = getCallableErrorCode(error);
    const status = code ? mapHttpsErrorToStatus(code) : 500;
    res.status(status).json({ error: error instanceof Error ? error.message : "Unknown error" });
  }
});

app.post("/admin/curation/step", async (req, res) => {
  try {
    await requireAdminBearer(req.headers.authorization ?? "");
    const jobId = String(req.body.jobId ?? "").trim();
    if (!jobId) {
      res.status(400).json({ error: "jobId is required" });
      return;
    }
    const hasMore = await advanceCurationJob(jobId);
    res.json({ jobId, hasMore });
  } catch (error) {
    const code = getCallableErrorCode(error);
    const status = code ? mapHttpsErrorToStatus(code) : 500;
    res.status(status).json({ error: error instanceof Error ? error.message : "Unknown error" });
  }
});

app.get("/admin/curation/status/:jobId", async (req, res) => {
  try {
    await requireAdminBearer(req.headers.authorization ?? "");
    const job = await getCurationJob(req.params.jobId);
    if (!job) {
      res.status(404).json({ error: "Job not found" });
      return;
    }
    res.json(job);
  } catch (error) {
    const code = getCallableErrorCode(error);
    const status = code ? mapHttpsErrorToStatus(code) : 500;
    res.status(status).json({ error: error instanceof Error ? error.message : "Unknown error" });
  }
});

function getCallableErrorCode(error: unknown): string | undefined {
  if (error && typeof error === "object" && "code" in error) {
    const code = (error as { code?: unknown }).code;
    if (typeof code === "string") return code;
  }
  return undefined;
}

function mapHttpsErrorToStatus(code: string): number {
  switch (code) {
    case "invalid-argument":
      return 400;
    case "unauthenticated":
      return 401;
    case "permission-denied":
      return 403;
    case "not-found":
      return 404;
    case "resource-exhausted":
      return 429;
    case "failed-precondition":
      return 412;
    default:
      return 500;
  }
}

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  console.log(`GuideMe guide-pack service listening on ${port}`);
});
