import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

admin.initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

// keep any other exports like cleanupStaleRuns
export { cleanupStaleRuns } from "./cleanupRuns";

/** ---------- helpers ---------- */
function sanitizeTopicId(raw: string) {
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
  logger.info("topic subscription changed", { topic, subscribe });
  return { ok: true };
});

/** ---------- triggers: notify when a run starts ---------- */
export const notifyRunCreatedActive = onDocumentCreated("runs/{runId}", async (event) => {
  const run = event.data?.data() || {};
  if (run.status !== "active") return;

  const courtId = String(run.courtId ?? "");
  if (!courtId) return;

  const courtName = (await getCourtName(courtId)) ?? (run.courtName ?? "A court");
  const topic = `court_${sanitizeTopicId(courtId)}`;

  logger.info("notifyRunCreatedActive", { runId: event.params.runId, courtId, topic });

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
    android: {
      priority: "high",
      notification: { channelId: "runs" },
    },
  });
});

export const notifyRunBecameActive = onDocumentUpdated("runs/{runId}", async (event) => {
  const before = event.data?.before?.data() || {};
  const after  = event.data?.after?.data()  || {};
  const becameActive = before.status !== "active" && after.status === "active";
  if (!becameActive) return;

  const courtId = String(after.courtId ?? "");
  if (!courtId) return;

  const courtName = (await getCourtName(courtId)) ?? (after.courtName ?? "A court");
  const topic = `court_${sanitizeTopicId(courtId)}`;

  logger.info("notifyRunBecameActive", { runId: event.params.runId, courtId, topic });

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
    android: {
      priority: "high",
      notification: { channelId: "runs" },
    },
  });
});
