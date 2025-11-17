import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";

admin.initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

// keep any other exports like cleanupStaleRuns
export { cleanupStaleRuns, purgeOldFinishedRuns } from "./cleanupRuns";

const db = admin.firestore();
const messaging = admin.messaging();


/** ---------- helpers ---------- */
function sanitizeTopicId(raw: string) {
  return raw.replace(/[^A-Za-z0-9_\-.]/g, "_");
}

// helpers at top of file (optional but handy)
function tsMillis(x: any | undefined): number | null {
  // Accepts Firestore Timestamp-like, Date, number, or undefined
  if (!x) return null;
  // Firestore Timestamp
  if (typeof x?.toMillis === "function") return x.toMillis();
  // JS Date
  if (x instanceof Date) return x.getTime();
  // number (assume ms)
  if (typeof x === "number") return x;
  return null;
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
  const snap = event.data;
  if (!snap) return;
  const run = snap.data() as any;
  if (run?.status !== "active") return;

  const runId = run.runId || event.params.runId;
  const courtId = String(run.courtId ?? "");
  if (!courtId) return;

  const mode = String(run.mode ?? "5v5");
  const maxPlayers = Number(run.maxPlayers ?? 10);

  // Prefer startsAt, then startTime, then createdAt, then now
  const startsAtMs =
    tsMillis(run.startsAt) ??
    tsMillis(run.startTime) ??
    tsMillis(run.createdAt) ??
    Date.now();

  const courtName =
    (await getCourtName(courtId)) ??
    (run.courtName as string | undefined) ??
    "A court";

  const topic = `court_${sanitizeTopicId(courtId)}`;

  logger.info("notifyRunCreatedActive", { runId, courtId, topic });

  // Send DATA message with rich fields (Android builds its own UI)
  await admin.messaging().send({
    topic,
    data: {
      type: "run_created",
      runId,
      courtId,
      courtName,
      mode,
      maxPlayers: String(maxPlayers),
      startsAt: String(startsAtMs),
    },
    // Keep channel hint for older Android if it ever falls back to notif-only
    android: { priority: "high", notification: { channelId: "runs" } },
  });
});

export const notifyRunBecameActive = onDocumentUpdated("runs/{runId}", async (event) => {
  const before = event.data?.before?.data() || {};
  const after  = event.data?.after?.data()  || {};
  const becameActive = before.status !== "active" && after.status === "active";
  if (!becameActive) return;

  const runId = (after.runId as string) || event.params.runId;
  const courtId = String(after.courtId ?? "");
  if (!courtId) return;

  const mode = String(after.mode ?? "5v5");
  const maxPlayers = Number(after.maxPlayers ?? 10);

  // If it was scheduled, startsAt should already be set; otherwise fallbacks
  const startsAtMs =
    tsMillis(after.startsAt) ??
    tsMillis(after.startTime) ??
    tsMillis(after.createdAt) ??
    Date.now();

  const courtName =
    (await getCourtName(courtId)) ??
    (after.courtName as string | undefined) ??
    "A court";

  const topic = `court_${sanitizeTopicId(courtId)}`;

  logger.info("notifyRunBecameActive", { runId, courtId, topic });

  // Keep a visible notification for older clients, but also send DATA for new UI
  await admin.messaging().send({
    topic,
    notification: {
      title: "Run just started",
      body: `${courtName} • ${(after.playerCount ?? 1)}/${maxPlayers} players`,
    },
    data: {
      type: "run_became_active",
      runId,
      courtId,
      courtName,
      mode,
      maxPlayers: String(maxPlayers),
      startsAt: String(startsAtMs),
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


/** ---------- scheduled: notify players before a run starts ---------- */

async function notifyUpcomingWindow(
  windowStartMs: number,
  windowEndMs: number,
  leadMinutes: number,
  flagField: string
) {
  const db = admin.firestore();

  const startTs = admin.firestore.Timestamp.fromMillis(windowStartMs);
  const endTs   = admin.firestore.Timestamp.fromMillis(windowEndMs);

  const snap = await db
    .collection("runs")
    .where("status", "==", "active")
    .where("startsAt", ">=", startTs)
    .where("startsAt", "<", endTs)
    .get();

  if (snap.empty) return;

  for (const doc of snap.docs) {
    const run = doc.data() as any;

    // already notified for this window?
    if (run[flagField]) continue;

    const runId      = (run.runId as string) || doc.id;
    const courtId    = String(run.courtId ?? "");
    const runName    = String(run.name ?? "");
    const mode       = String(run.mode ?? "5v5");
    const maxPlayers = Number(run.maxPlayers ?? 10);
    const startsAtMs = tsMillis(run.startsAt) ?? Date.now();

    const playerIds: string[] = Array.isArray(run.playerIds) ? run.playerIds : [];
    if (!playerIds.length || !courtId) continue;

    const courtName =
      (await getCourtName(courtId)) ??
      (run.courtName as string | undefined) ??
      "your court";

    // collect all device tokens for these players
    const tokens = new Set<string>();
    for (const uid of playerIds) {
      const toks = await db
        .collection("users")
        .doc(uid)
        .collection("tokens")
        .get();
      toks.docs.forEach((d) => tokens.add(d.id));
    }
    if (!tokens.size) continue;

    const dataPayload: { [k: string]: string } = {
      type: "run_upcoming",
      runId,
      courtId,
      courtName,
      runName,
      mode,
      maxPlayers: String(maxPlayers),
      startsAt: String(startsAtMs),
      minutes: String(leadMinutes),
    };

    // send to each device token
    await Promise.all(
      Array.from(tokens).map((token) =>
        admin.messaging().send({
          token,
          data: dataPayload,
          android: {
            priority: "high",
            notification: { channelId: "runs" },
          },
        })
      )
    );

    // mark that we sent this window's notification
    await doc.ref.update({ [flagField]: true });
  }
}

export const notifyUpcomingRuns = onSchedule("every 5 minutes", async () => {
  const now = Date.now();
  const minute = 60 * 1000;

  // 1 hour window: now + 55..65 minutes
  await notifyUpcomingWindow(
    now + 55 * minute,
    now + 65 * minute,
    60,
    "upcoming1hNotified"
  );

  // 10 minute window: now + 5..15 minutes
  await notifyUpcomingWindow(
    now + 5 * minute,
    now + 15 * minute,
    10,
    "upcoming10mNotified"
  );
});

/**
 * When a run transitions from active → cancelled,
 * notify all players that the run was cancelled.
 */
export const notifyRunCancelled = onDocumentUpdated("runs/{runId}", async (event) => {
  const before = event.data?.before?.data();
  const after  = event.data?.after?.data();
  const runId  = event.params.runId;

  if (!before || !after) return;

  const prevStatus = before.status as string | undefined;
  const newStatus  = after.status as string | undefined;

  // Only fire when going from active -> cancelled
  if (prevStatus === newStatus) return;
  if (prevStatus !== "active" || newStatus !== "cancelled") return;

  const courtId   = after.courtId as string | undefined;
  const runName   = after.name as string | undefined;
  const playerIds = (after.playerIds as string[] | undefined) ?? [];

  if (!playerIds.length) {
    logger.info(`[notifyRunCancelled] No players on run ${runId}, skipping.`);
    return;
  }

  // --- Get court name (optional but nice) ---
  let courtName = "";
  if (courtId) {
    try {
      const courtSnap = await db.collection("courts").doc(courtId).get();
      courtName = (courtSnap.data()?.name as string | undefined) ?? "";
    } catch (err) {
      logger.warn(`[notifyRunCancelled] Failed to load court ${courtId}`, err);
    }
  }

  // --- Collect FCM tokens for all players ---
  const tokens: string[] = [];

  try {
    const tokenPromises = playerIds.map(async (uid) => {
      const snap = await db
        .collection("users")
        .doc(uid)
        .collection("tokens")    // same subcollection as notifyUpcomingWindow
        .get();

      snap.docs.forEach((doc: any) => {
        const t = doc.id;  // using doc ID as token, same as notifyUpcomingWindow
        if (t && typeof t === "string") {
          tokens.push(t);
        }
      });
    });

    await Promise.all(tokenPromises);
  } catch (err) {
    logger.error("[notifyRunCancelled] Error fetching tokens:", err);
    return;
  }

  if (!tokens.length) {
    logger.info(`[notifyRunCancelled] No tokens for run ${runId}, skipping send.`);
    return;
  }

  // --- Build message payload ---
  const payload: admin.messaging.MulticastMessage = {
    tokens,
    data: {
      type: "run_cancelled",
      runId,
      courtName: courtName || "",
      runName: runName || "",
    },
  };

  try {
    const resp = await messaging.sendEachForMulticast(payload);
    logger.info(
      `[notifyRunCancelled] Sent to ${tokens.length} tokens, success=${resp.successCount}, failure=${resp.failureCount}`
    );

    // Optional: log invalid tokens for cleanup
    resp.responses.forEach((r: any, idx: number) => {
      if (!r.success && r.error) {
        const errCode = r.error.code;
        if (
          errCode === "messaging/invalid-registration-token" ||
          errCode === "messaging/registration-token-not-registered"
        ) {
          logger.warn(
            `[notifyRunCancelled] Invalid token, consider deleting: ${tokens[idx]}`
          );
        }
      }
    });
  } catch (err) {
    logger.error("[notifyRunCancelled] Failed to send multicast:", err);
  }
});




