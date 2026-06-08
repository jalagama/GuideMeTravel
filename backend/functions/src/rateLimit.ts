import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";

type RateLimitConfig = {
  key: string;
  maxRequests: number;
  windowMs: number;
};

export async function enforceRateLimit(config: RateLimitConfig): Promise<void> {
  const ref = db.collection("rateLimits").doc(config.key);
  const now = Date.now();

  await db.runTransaction(async (transaction) => {
    const snap = await transaction.get(ref);
    const data = snap.data();
    const windowStart = typeof data?.windowStart === "number" ? data.windowStart : now;
    const count = typeof data?.count === "number" ? data.count : 0;

    if (now - windowStart > config.windowMs) {
      transaction.set(ref, { windowStart: now, count: 1, updatedAtMillis: now });
      return;
    }

    if (count >= config.maxRequests) {
      throw new HttpsError(
        "resource-exhausted",
        "Rate limit exceeded. Please try again later."
      );
    }

    transaction.set(ref, { windowStart, count: count + 1, updatedAtMillis: now });
  });
}
