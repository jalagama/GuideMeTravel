import { HttpsError } from "firebase-functions/v2/https";
import { advanceCurationJob, getCurationJob } from "./bulkCurateCountry";
import { buildCurationProgressView } from "./curationEstimate";
import { getGuideMeLogger } from "./logging/loggerContext";

const BATCH_MAX_STEPS = 200;
const BATCH_MAX_RUNTIME_MS = 480_000;

export type CurationBatchResult = {
  jobId: string;
  stepsProcessed: number;
  hasMore: boolean;
  status: string;
  phase: string;
  progress: ReturnType<typeof buildCurationProgressView>;
  elapsedMs: number;
};

export async function runCurationBatch(jobId: string): Promise<CurationBatchResult> {
  const started = Date.now();
  let steps = 0;
  let hasMore = true;

  const initial = await getCurationJob(jobId);
  if (!initial) {
    throw new HttpsError("not-found", `Curation job ${jobId} not found.`);
  }
  if (initial.status === "completed" || initial.status === "failed") {
    return {
      jobId,
      stepsProcessed: 0,
      hasMore: false,
      status: initial.status,
      phase: initial.phase,
      progress: buildCurationProgressView(initial),
      elapsedMs: 0,
    };
  }

  while (
    hasMore &&
    steps < BATCH_MAX_STEPS &&
    Date.now() - started < BATCH_MAX_RUNTIME_MS
  ) {
    hasMore = await advanceCurationJob(jobId);
    steps += 1;
  }

  const job = await getCurationJob(jobId);
  if (!job) {
    throw new HttpsError("not-found", `Curation job ${jobId} not found after batch.`);
  }

  return {
    jobId,
    stepsProcessed: steps,
    hasMore: hasMore && job.status === "running",
    status: job.status,
    phase: job.phase,
    progress: buildCurationProgressView(job),
    elapsedMs: Date.now() - started,
  };
}

/** Fire-and-forget HTTP trigger for the next batch (server-side auto-continue). */
export async function scheduleCurationBatch(jobId: string): Promise<void> {
  const firebaseConfigJson = process.env.FIREBASE_CONFIG?.trim();
  const projectId =
    process.env.GCLOUD_PROJECT?.trim() ||
    process.env.GCP_PROJECT?.trim() ||
    (firebaseConfigJson
      ? (JSON.parse(firebaseConfigJson) as { projectId?: string }).projectId
      : undefined);

  const secret = resolveWorkerSecret();
  if (!projectId || !secret) {
    getGuideMeLogger().warn("curation_auto_schedule_skipped", {
      jobId,
      reason: !projectId ? "missing_project" : "missing_secret",
    });
    return;
  }

  const region = "asia-south1";
  const url = `https://${region}-${projectId}.cloudfunctions.net/adminProcessCurationBatch`;

  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Curation-Worker-Secret": secret,
    },
    body: JSON.stringify({ jobId }),
  }).catch((error) => {
    getGuideMeLogger().error("curation_batch_schedule_failed", {
      jobId,
      error: error instanceof Error ? error.message : String(error),
    });
  });
}

export function resolveWorkerSecret(): string | undefined {
  const explicit = process.env.CURATION_WORKER_SECRET?.trim();
  if (explicit) return explicit;
  const projectId = process.env.GCLOUD_PROJECT?.trim() || process.env.GCP_PROJECT?.trim();
  if (!projectId) return undefined;
  return `guideme-curation-${projectId}`;
}

export function verifyWorkerSecret(header: string | undefined): boolean {
  const expected = resolveWorkerSecret();
  if (!expected) return false;
  return header === expected;
}

export async function runCurationBatchAndSchedule(jobId: string): Promise<CurationBatchResult> {
  const result = await runCurationBatch(jobId);
  if (result.hasMore && result.status === "running") {
    await scheduleCurationBatch(jobId);
  }
  return result;
}
