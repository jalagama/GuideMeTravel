import express from "express";
import * as admin from "firebase-admin";
import { HttpsError } from "firebase-functions/v2/https";
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { generateGuidePackForTrip } = require("../../functions/lib/generateGuidePack");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { enforceRateLimit } = require("../../functions/lib/rateLimit");
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { logEvent, toHttpsError } = require("../../functions/lib/utils");

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
    const httpsError = toHttpsError(error);
    const status =
      httpsError instanceof HttpsError
        ? mapHttpsErrorToStatus(httpsError.code)
        : 500;
    const message = error instanceof Error ? error.message : "Unknown error";
    logEvent("generate_guide_pack_failed", { error: message, status });
    res.status(status).json({ error: message });
  }
});

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
