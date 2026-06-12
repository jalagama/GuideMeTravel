import { HttpsError, CallableRequest } from "firebase-functions/v2/https";

export function requireAdmin(request: CallableRequest): string {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  if (request.auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Admin access required.");
  }
  return request.auth.uid;
}

export async function requireAdminBearer(authHeader: string): Promise<string> {
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  if (!token) {
    throw new HttpsError("unauthenticated", "Missing bearer token.");
  }
  const admin = await import("firebase-admin");
  const decoded = await admin.auth().verifyIdToken(token);
  if (decoded.admin !== true) {
    throw new HttpsError("permission-denied", "Admin access required.");
  }
  return decoded.uid;
}
