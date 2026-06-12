import { HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

/** One-time bootstrap: set admin claim for allowlisted UIDs. */
export async function setAdminClaimForUid(uid: string, callerUid: string): Promise<void> {
  const allowlist = (process.env.ADMIN_BOOTSTRAP_UIDS ?? "")
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);

  if (allowlist.length > 0 && !allowlist.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Caller is not on the admin bootstrap allowlist.");
  }

  await admin.auth().setCustomUserClaims(uid, { admin: true });
}
