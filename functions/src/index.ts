// functions/src/index.ts
import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

admin.initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

// Keep other exports
export { cleanupStaleRuns } from "./cleanupRuns";

/** ---------- helpers ---------- */
function sanitizeTopicId(raw: string) {
  // Topics allow letters, numbers, _ - .
  return raw.replace(/[^A-Za-z0-9_\-.]/g, "_");
}

async function getCourtName(courtId: string) {
  try {
    const snap = await admin.firestore().collection("courts").doc(courtId).get();
    const data = snap.data();
    return (data?.name as string) || null;
  } catch (e) {
    logger.warn("getCourtName failed", { courtId, e });
    return null;
  }
}

/** ---------- callable: subscribe/unsubscribe ---------- */
export const setCourtTopicSubscription = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");

  const { token, courtId, subscribe } = (request.data ?? {}) as {
    token?: string; courtId?: string; subscribe?: boolean;
  };
  if (!token || !courtId) throw new HttpsError("invalid-argument", "token and courtId required");

  const topic = `court_${sanitizeTopicId(courtId)}`;
  if (subscribe) await admin.messaging().subscribeToTopic([token], topic);
  else           await admin.messaging().unsubscribeFromTopic([token], topic);
  return { ok: true };
});

/** ---------- triggers: notify when a run starts ---------- */
// on create, if status is already "active"
export const notifyRunCreatedActive = onDocumentCreated("runs/{runId}", async (event) => {
  const run = event.data?.data() || {};
  if (run.status !== "active") return;

  const courtId = String(run.courtId ?? "");
  if (!courtId) return;

  const courtName = (await getCourtName(courtId)) ?? (run.courtName ?? "A court");
  const topic = `court_${sanitizeTopicId(courtId)}`;

  await admin.messaging().send({
    topic,
    notification: {
      title: "Run just started",
      body: `${courtName} • ${(run.playerCount ?? 1)}/${(run.maxPlayers ?? 10)} players`,
    },
    data: {
      type: "run_open",
      courtId,
      runId: event.params.runId,
      courtName,
    },
  });
});

// on update, when status changes to "active"
export const notifyRunBecameActive = onDocumentUpdated("runs/{runId}", async (event) => {
  const before = event.data?.before?.data() || {};
  const after  = event.data?.after?.data()  || {};
  const becameActive = before.status !== "active" && after.status === "active";
  if (!becameActive) return;

  const courtId = String(after.courtId ?? "");
  if (!courtId) return;

  const courtName = (await getCourtName(courtId)) ?? (after.courtName ?? "A court");
  const topic = `court_${sanitizeTopicId(courtId)}`;

  await admin.messaging().send({
    topic,
    notification: {
      title: "Run just started",
      body: `${courtName} • ${(after.playerCount ?? 1)}/${(after.maxPlayers ?? 10)} players`,
    },
    data: {
      type: "run_open",
      courtId,
      runId: event.params.runId,
      courtName,
    },
  });
});
