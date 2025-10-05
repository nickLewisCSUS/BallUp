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
    data: {
      type: "run_open",
      courtId,
      runId: event.params.runId,
      courtName,
    },
    android: { priority: "high", notification: { channelId: "runs" } },
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
    android: { priority: "high", notification: { channelId: "runs" } },
  });
});

/** ---------- NEW: notify when spots change (joins/leaves) ---------- */
export const notifyRunSpotsChange = onDocumentUpdated("runs/{runId}", async (event) => {
  const before = (event.data?.before?.data() || {}) as any;
  const after  = (event.data?.after?.data()  || {}) as any;

  if (after.status !== "active") return;

  const pcBefore = Number(before.playerCount ?? 0);
  const pcAfter  = Number(after.playerCount ?? 0);
  if (pcBefore === pcAfter) return; // ignore non-capacity updates (e.g., lastAlertAt set)

  const max = Number(after.maxPlayers ?? 10);
  const beforeSlots = Math.max(0, max - pcBefore);
  const afterSlots  = Math.max(0, max - pcAfter);
  const left = pcAfter < pcBefore;

  const courtId = String(after.courtId ?? "");
  if (!courtId) return;
  const courtName = (await getCourtName(courtId)) ?? (after.courtName ?? "A court");
  const topic = `court_${sanitizeTopicId(courtId)}`;

  // throttle: 2 minutes between alerts for the same run
  const minGapMs = 2 * 60 * 1000;
  const lastAlertAt = after.lastAlertAt as admin.firestore.Timestamp | undefined;
  const lastMs = lastAlertAt?.toMillis?.() ?? 0;
  const nowMs = Date.now();
  if (lastMs && nowMs - lastMs < minGapMs) {
    logger.info("notifyRunSpotsChange: throttled", { runId: event.params.runId, lastMs, nowMs });
    return;
  }

  // decide whether to send and craft title
  let title: string | null = null;

  // A spot opened (was full -> not full)
  if (left && beforeSlots === 0 && afterSlots > 0) {
    title = afterSlots === 1 ? "A spot just opened" : `${afterSlots} spots just opened`;
  }

  // Low capacity: 1–3 spots left (on join or leave)
  if (!title && afterSlots >= 1 && afterSlots <= 3) {
    title = afterSlots === 1 ? "1 spot left" : `${afterSlots} spots left`;
  }

  if (!title) return;

  logger.info("notifyRunSpotsChange: sending", {
    runId: event.params.runId,
    courtId, pcBefore, pcAfter, max, afterSlots, title
  });

  await admin.messaging().send({
    topic,
    notification: {
      title,
      body: `${courtName} • ${pcAfter}/${max} playing`,
    },
    data: {
      type: "run_spots",
      courtId,
      runId: event.params.runId,
      courtName,
      slotsLeft: String(afterSlots),
    },
    android: { priority: "high", notification: { channelId: "runs" } },
  });

  // mark alert time to throttle future sends
  await admin.firestore().collection("runs").doc(event.params.runId).update({
    lastAlertAt: admin.firestore.FieldValue.serverTimestamp(),
  });
});

