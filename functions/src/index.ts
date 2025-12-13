// index.ts
import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentDeleted,
} from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";

admin.initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

export { cleanupStaleRuns, purgeOldFinishedRuns } from "./cleanupRuns";
export {
  createRunInvitesForNewRun,
  createRunInvitesOnAllowedUidsAdded,
  deleteRunInvitesOnAllowedUidsRemoved,
  notifyRunInviteCreated,
} from "./runInvites";
const db = admin.firestore();
const messaging = admin.messaging();

/** ---------- helpers ---------- */
function sanitizeTopicId(raw: string) {
  return raw.replace(/[^A-Za-z0-9_\-.]/g, "_");
}

function tsMillis(x: any | undefined): number | null {
  if (!x) return null;
  if (typeof x?.toMillis === "function") return x.toMillis();
  if (x instanceof Date) return x.getTime();
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
    token?: string;
    courtId?: string;
    subscribe?: boolean;
  };
  if (!token || !courtId)
    throw new HttpsError("invalid-argument", "token and courtId required");

  const topic = `court_${sanitizeTopicId(courtId)}`;
  if (subscribe) await admin.messaging().subscribeToTopic([token], topic);
  else await admin.messaging().unsubscribeFromTopic([token], topic);
  logger.info("topic subscription changed", { topic, subscribe });
  return { ok: true };
});

/** ---------- runs: created active ---------- */
export const notifyRunCreatedActive = onDocumentCreated(
  "runs/{runId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const run = snap.data() as any;
    if (run?.status !== "active") return;

    const runId = run.runId || event.params.runId;
    const courtId = String(run.courtId ?? "");
    if (!courtId) return;

    const mode = String(run.mode ?? "5v5");
    const maxPlayers = Number(run.maxPlayers ?? 10);

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

    const notifTitle = `New run at ${courtName}`;
    const notifBody = `${mode} • up to ${maxPlayers} players`;

    await admin.messaging().send({
      topic,
      notification: {
        title: notifTitle,
        body: notifBody,
      },
      data: {
        type: "run_created",
        runId,
        courtId,
        courtName,
        mode,
        maxPlayers: String(maxPlayers),
        startsAt: String(startsAtMs),
      },
      android: { priority: "high", notification: { channelId: "runs" } },
    });
  }
);

/** ---------- runs: became active ---------- */
export const notifyRunBecameActive = onDocumentUpdated(
  "runs/{runId}",
  async (event) => {
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};
    const becameActive =
      before.status !== "active" && after.status === "active";
    if (!becameActive) return;

    const runId = (after.runId as string) || event.params.runId;
    const courtId = String(after.courtId ?? "");
    if (!courtId) return;

    const mode = String(after.mode ?? "5v5");
    const maxPlayers = Number(after.maxPlayers ?? 10);

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
  }
);

/** ---------- runs: spots change ---------- */
export const notifyRunSpotsChange = onDocumentUpdated(
  "runs/{runId}",
  async (event) => {
    const before = (event.data?.before?.data() || {}) as any;
    const after = (event.data?.after?.data() || {}) as any;

    if (after.status !== "active") return;

    const pcBefore = Number(before.playerCount ?? 0);
    const pcAfter = Number(after.playerCount ?? 0);
    if (pcBefore === pcAfter) return;

    const max = Number(after.maxPlayers ?? 10);
    const beforeSlots = Math.max(0, max - pcBefore);
    const afterSlots = Math.max(0, max - pcAfter);
    const left = pcAfter < pcBefore;

    const courtId = String(after.courtId ?? "");
    if (!courtId) return;
    const courtName =
      (await getCourtName(courtId)) ?? (after.courtName ?? "A court");
    const topic = `court_${sanitizeTopicId(courtId)}`;

    const minGapMs = 2 * 60 * 1000;
    const lastAlertAt =
      (after.lastAlertAt as admin.firestore.Timestamp | undefined) ?? null;
    const lastMs = lastAlertAt?.toMillis?.() ?? 0;
    const nowMs = Date.now();
    if (lastMs && nowMs - lastMs < minGapMs) {
      logger.info("notifyRunSpotsChange: throttled", {
        runId: event.params.runId,
        lastMs,
        nowMs,
      });
      return;
    }

    let title: string | null = null;

    if (left && beforeSlots === 0 && afterSlots > 0) {
      title =
        afterSlots === 1
          ? "A spot just opened"
          : `${afterSlots} spots just opened`;
    }

    if (!title && afterSlots >= 1 && afterSlots <= 3) {
      title =
        afterSlots === 1 ? "1 spot left" : `${afterSlots} spots left`;
    }

    if (!title) return;

    logger.info("notifyRunSpotsChange: sending", {
      runId: event.params.runId,
      courtId,
      pcBefore,
      pcAfter,
      max,
      afterSlots,
      title,
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

    await admin
      .firestore()
      .collection("runs")
      .doc(event.params.runId)
      .update({
        lastAlertAt: admin.firestore.FieldValue.serverTimestamp(),
      });
  }
);

/** ---------- scheduled: upcoming runs ---------- */
async function notifyUpcomingWindow(
  windowStartMs: number,
  windowEndMs: number,
  leadMinutes: number,
  flagField: string
) {
  const db = admin.firestore();

  const startTs = admin.firestore.Timestamp.fromMillis(windowStartMs);
  const endTs = admin.firestore.Timestamp.fromMillis(windowEndMs);

  const snap = await db
    .collection("runs")
    .where("status", "==", "active")
    .where("startsAt", ">=", startTs)
    .where("startsAt", "<", endTs)
    .get();

  logger.info("notifyUpcomingWindow query", {
    leadMinutes,
    windowStartMs,
    windowEndMs,
    matchedCount: snap.size,
  });

  if (snap.empty) return;

  for (const doc of snap.docs) {
    const run = doc.data() as any;

    if (run[flagField]) continue;

    const runId = (run.runId as string) || doc.id;
    const courtId = String(run.courtId ?? "");
    const runName = String(run.name ?? "");
    const mode = String(run.mode ?? "5v5");
    const maxPlayers = Number(run.maxPlayers ?? 10);
    const startsAtMs = tsMillis(run.startsAt) ?? Date.now();

    const playerIds: string[] = Array.isArray(run.playerIds)
      ? run.playerIds
      : [];
    if (!playerIds.length || !courtId) continue;

    const courtName =
      (await getCourtName(courtId)) ??
      (run.courtName as string | undefined) ??
      "your court";

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

    const minutesText =
      leadMinutes === 60
        ? "in 1 hour"
        : leadMinutes === 10
        ? "in 10 minutes"
        : `in ${leadMinutes} minutes`;

    const notifTitle = runName || "Your run is coming up";
    const notifBody = courtName
      ? `Starts ${minutesText} at ${courtName}`
      : `Starts ${minutesText}`;

    await Promise.all(
      Array.from(tokens).map((token) =>
        admin.messaging().send({
          token,
          notification: {
            title: notifTitle,
            body: notifBody,
          },
          data: dataPayload,
          android: {
            priority: "high",
            notification: { channelId: "runs" },
          },
        })
      )
    );

    await doc.ref.update({ [flagField]: true });
  }
}

export const notifyUpcomingRuns = onSchedule("every 5 minutes", async () => {
  const now = Date.now();
  logger.info("notifyUpcomingRuns tick", { now });

  const minute = 60 * 1000;

  await notifyUpcomingWindow(
    now + 55 * minute,
    now + 65 * minute,
    60,
    "upcoming1hNotified"
  );

  await notifyUpcomingWindow(
    now + 5 * minute,
    now + 15 * minute,
    10,
    "upcoming10mNotified"
  );
});

/** ---------- runs: cancelled ---------- */
export const notifyRunCancelled = onDocumentUpdated(
  "runs/{runId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    const runId = event.params.runId;

    if (!before || !after) return;

    const prevStatus = before.status as string | undefined;
    const newStatus = after.status as string | undefined;

    logger.info("[notifyRunCancelled] trigger", {
      runId,
      prevStatus,
      newStatus,
    });

    const becameCancelled =
      newStatus === "cancelled" && prevStatus !== "cancelled";
    if (!becameCancelled) return;

    if (prevStatus === newStatus) return;
    if (prevStatus !== "active" || newStatus !== "cancelled") return;

    const courtId = after.courtId as string | undefined;
    const runName = after.name as string | undefined;
    const playerIds = (after.playerIds as string[] | undefined) ?? [];

    if (!playerIds.length) {
      logger.info(
        `[notifyRunCancelled] No players on run ${runId}, skipping.`
      );
      return;
    }

    let courtName = "";
    if (courtId) {
      try {
        const courtSnap = await db.collection("courts").doc(courtId).get();
        courtName = (courtSnap.data()?.name as string | undefined) ?? "";
      } catch (err) {
        logger.warn(
          `[notifyRunCancelled] Failed to load court ${courtId}`,
          err
        );
      }
    }

    const tokens: string[] = [];

    try {
      const tokenPromises = playerIds.map(async (uid) => {
        const snap = await db
          .collection("users")
          .doc(uid)
          .collection("tokens")
          .get();

        snap.docs.forEach((doc: any) => {
          const t = doc.id;
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
      logger.info(
        `[notifyRunCancelled] No tokens for run ${runId}, skipping send.`
      );
      return;
    }

    const title = runName
      ? `Run cancelled: ${runName}`
      : courtName
      ? `Run cancelled at ${courtName}`
      : "A run you joined was cancelled";

    const body = courtName
      ? `The run at ${courtName} has been cancelled.`
      : "This run has been cancelled.";

    const payload: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        type: "run_cancelled",
        runId,
        courtName: courtName || "",
        runName: runName || "",
      },
      android: {
        priority: "high",
        notification: { channelId: "runs" },
      },
    };

    try {
      const resp = await messaging.sendEachForMulticast(payload);
      logger.info(
        `[notifyRunCancelled] Sent to ${tokens.length} tokens, success=${resp.successCount}, failure=${resp.failureCount}`
      );
    } catch (err) {
      logger.error("[notifyRunCancelled] Failed to send multicast:", err);
    }
  }
);

/** ---------- team invite created ---------- */
export const notifyTeamInviteCreated = onDocumentCreated(
  "teamInvites/{inviteId}",
  async (event) => {
    const inviteId = event.params.inviteId;
    const invite = event.data?.data() as any;

    if (!invite) {
      logger.warn("[notifyTeamInviteCreated] no invite data", { inviteId });
      return;
    }

    const uid =
      (invite.userId as string | undefined) ??
      (invite.uid as string | undefined) ??
      "";

    if (!uid) {
      logger.warn("[notifyTeamInviteCreated] invite missing uid/userId", {
        inviteId,
        invite,
      });
      return;
    }

    const teamId = (invite.teamId as string | undefined) ?? "";
    const teamName =
      (invite.teamName as string | undefined) ??
      (invite.name as string | undefined) ??
      "A squad";

    const ownerName = (invite.ownerName as string | undefined) ?? "";

    const tokens: string[] = [];
    try {
      const snap = await db
        .collection("users")
        .doc(uid)
        .collection("tokens")
        .get();

      snap.docs.forEach((doc) => {
        const t = doc.id;
        if (t && typeof t === "string") tokens.push(t);
      });
    } catch (err) {
      logger.error("[notifyTeamInviteCreated] error fetching tokens", {
        uid,
        err,
      });
      return;
    }

    if (!tokens.length) {
      logger.info("[notifyTeamInviteCreated] no tokens for user, skipping", {
        uid,
      });
      return;
    }

    const title =
      ownerName && teamName
        ? `${ownerName} invited you to join ${teamName}`
        : teamName
        ? `Squad invite: ${teamName}`
        : "New squad invite";

    const body = "Tap to view your squad invites in BallUp";

    const payload: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        type: "team_invite",
        inviteId,
        teamId,
        teamName,
        ownerName,
      },
      android: {
        priority: "high",
      },
    };

    try {
      const resp = await messaging.sendEachForMulticast(payload);
      logger.info("[notifyTeamInviteCreated] sent squad invite notif", {
        uid,
        inviteId,
        teamId,
        teamName,
        success: resp.successCount,
        failure: resp.failureCount,
      });
    } catch (err) {
      logger.error("[notifyTeamInviteCreated] sendEachForMulticast failed", {
        err,
      });
    }
  }
);

/** ---------- team invite accepted (player accepts invite → notify owner) ---------- */
export const notifyTeamInviteAccepted = onDocumentUpdated(
  "teamInvites/{inviteId}",
  async (event) => {
    const inviteId = event.params.inviteId;
    const before = event.data?.before?.data() as any | undefined;
    const after = event.data?.after?.data() as any | undefined;

    logger.info("[notifyTeamInviteAccepted] trigger", {
      inviteId,
      beforeStatus: before?.status,
      afterStatus: after?.status,
    });

    if (!before || !after) {
      logger.warn("[notifyTeamInviteAccepted] missing before/after", { inviteId });
      return;
    }

    const prevStatus = (before.status as string | undefined) ?? "";
    const newStatus = (after.status as string | undefined) ?? "";

    if (prevStatus === newStatus || newStatus !== "accepted") {
      logger.info("[notifyTeamInviteAccepted] not a new accepted status, skipping", {
        inviteId,
        prevStatus,
        newStatus,
      });
      return;
    }

    const teamId = (after.teamId as string | undefined) ?? "";
    const invitedUid = (after.uid as string | undefined) ?? "";

    if (!teamId || !invitedUid) {
      logger.warn("[notifyTeamInviteAccepted] missing teamId or invited uid", {
        inviteId,
        teamId,
        invitedUid,
      });
      return;
    }

    let ownerUid =
      (after.ownerUid as string | undefined) ??
      (before.ownerUid as string | undefined) ??
      "";

    let teamName =
      (after.teamName as string | undefined) ??
      (before.teamName as string | undefined) ??
      "";

    if (!ownerUid || !teamName) {
      try {
        const snap = await db.collection("teams").doc(teamId).get();
        if (snap.exists) {
          const team = snap.data() as any;
          if (!ownerUid) {
            ownerUid =
              (team.ownerUid as string | undefined) ??
              (team.ownerId as string | undefined) ??
              "";
          }
          if (!teamName) {
            teamName =
              (team.teamName as string | undefined) ??
              (team.name as string | undefined) ??
              "your squad";
          }
        } else {
          logger.warn("[notifyTeamInviteAccepted] team doc not found", {
            inviteId,
            teamId,
          });
        }
      } catch (err) {
        logger.warn("[notifyTeamInviteAccepted] failed to load team doc", {
          inviteId,
          teamId,
          err,
        });
      }
    }

    if (!ownerUid) {
      logger.warn("[notifyTeamInviteAccepted] missing ownerUid after fallback", {
        inviteId,
        teamId,
      });
      return;
    }

    let playerName =
      (after.playerName as string | undefined) ??
      (before.playerName as string | undefined) ??
      "";

    if (!playerName && invitedUid) {
      try {
        const userSnap = await db.collection("users").doc(invitedUid).get();
        const u = userSnap.data() || {};
        playerName =
          (u.username as string | undefined) ??
          (u.displayName as string | undefined) ??
          "A player";
      } catch (err) {
        logger.warn("[notifyTeamInviteAccepted] failed to load player profile", {
          invitedUid,
          err,
        });
        playerName = "A player";
      }
    }

    const tokensSnap = await db
      .collection("users")
      .doc(ownerUid)
      .collection("tokens")
      .get();

    if (tokensSnap.empty) {
      logger.warn("[notifyTeamInviteAccepted] no tokens for owner", {
        inviteId,
        ownerUid,
      });
      return;
    }

    const tokens = tokensSnap.docs.map((d) => d.id);

    const title = (() => {
      if (teamName && playerName && playerName !== "A player") {
        return `${playerName} joined ${teamName}`;
      }
      if (teamName) return `Invite accepted for ${teamName}`;
      return "Squad invite accepted";
    })();

    const body = (() => {
      if (playerName && playerName !== "A player") {
        return `${playerName} accepted your squad invite.`;
      }
      return "Your squad invite was accepted.";
    })();

    const msg: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        type: "team_invite_accepted",
        inviteId,
        teamId,
        teamName,
        playerName,
      },
      android: {
        priority: "high",
      },
    };

    try {
      const resp = await messaging.sendEachForMulticast(msg);
      logger.info("[notifyTeamInviteAccepted] sent", {
        inviteId,
        ownerUid,
        teamId,
        teamName,
        playerName,
        success: resp.successCount,
        failure: resp.failureCount,
      });
    } catch (err) {
      logger.error("[notifyTeamInviteAccepted] multicast send failed", { err });
    }
  }
);

/** ---------- team join approved (host approves join request) ---------- */
export const notifyTeamJoinApproved = onDocumentUpdated(
  "teamJoinRequests/{requestId}",
  async (event) => {
    const requestId = event.params.requestId;
    const before = event.data?.before?.data() as any | undefined;
    const after = event.data?.after?.data() as any | undefined;

    logger.info("[notifyTeamJoinApproved] trigger", {
      requestId,
      beforeStatus: before?.status,
      afterStatus: after?.status,
    });

    if (!before || !after) {
      logger.warn("[notifyTeamJoinApproved] missing before/after", { requestId });
      return;
    }

    const prevStatus = (before.status as string | undefined) ?? "";
    const newStatus = (after.status as string | undefined) ?? "";

    if (prevStatus === newStatus) {
      logger.info("[notifyTeamJoinApproved] status unchanged, skipping", {
        requestId,
        status: newStatus,
      });
      return;
    }

    if (newStatus !== "approved") {
      logger.info("[notifyTeamJoinApproved] not approved → skipping", {
        requestId,
        prevStatus,
        newStatus,
      });
      return;
    }

    const teamId = (after.teamId as string | undefined) ?? "";
    const requesterUid = (after.uid as string | undefined) ?? "";

    if (!teamId || !requesterUid) {
      logger.warn("[notifyTeamJoinApproved] missing teamId or uid", {
        requestId,
        teamId,
        requesterUid,
      });
      return;
    }

    const teamSnap = await db.collection("teams").doc(teamId).get();
    if (!teamSnap.exists) {
      logger.warn("[notifyTeamJoinApproved] team not found", { requestId, teamId });
      return;
    }
    const team = teamSnap.data() as any;
    const teamName =
      (team.name as string | undefined) ??
      (after.teamName as string | undefined) ??
      "your squad";

    const tokensSnap = await db
      .collection("users")
      .doc(requesterUid)
      .collection("tokens")
      .get();

    if (tokensSnap.empty) {
      logger.warn("[notifyTeamJoinApproved] no tokens for requester", {
        requestId,
        requesterUid,
      });
      return;
    }

    const tokens = tokensSnap.docs.map((d) => d.id);

    const title = teamName
      ? `You're in: ${teamName}`
      : "Your squad request was approved";
    const body = teamName
      ? `You were added to ${teamName}.`
      : "Your squad join request was approved.";

    const msg: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        type: "team_join_approved",
        requestId,
        teamId,
        teamName,
      },
      android: {
        priority: "high",
      },
    };

    try {
      const resp = await messaging.sendEachForMulticast(msg);
      logger.info("[notifyTeamJoinApproved] sent", {
        requestId,
        requesterUid,
        teamId,
        teamName,
        success: resp.successCount,
        failure: resp.failureCount,
      });
    } catch (err) {
      logger.error("[notifyTeamJoinApproved] multicast send failed", { err });
    }
  }
);

/**
 * Notify squad owner when someone requests to join.
 */
export const notifyTeamJoinRequested = onDocumentCreated(
  "teamJoinRequests/{requestId}",
  async (event) => {
    const requestId = event.params.requestId;
    const req = event.data?.data() as any;

    if (!req) {
      logger.warn("[notifyTeamJoinRequested] no request data", { requestId });
      return;
    }

    const teamId = (req.teamId as string | undefined) ?? "";

    let ownerUid =
      (req.ownerUid as string | undefined) ??
      (req.ownerId as string | undefined) ??
      "";

    let teamName =
      (req.teamName as string | undefined) ??
      (req.name as string | undefined) ??
      "";

    if (teamId && (!ownerUid || !teamName)) {
      try {
        const teamSnap = await db.collection("teams").doc(teamId).get();
        const team = teamSnap.data() as any | undefined;

        if (team) {
          if (!ownerUid) {
            ownerUid =
              (team.ownerUid as string | undefined) ??
              (team.ownerId as string | undefined) ??
              "";
          }
          if (!teamName) {
            teamName =
              (team.teamName as string | undefined) ??
              (team.name as string | undefined) ??
              "your squad";
          }
        } else {
          logger.warn("[notifyTeamJoinRequested] team doc not found", {
            requestId,
            teamId,
          });
        }
      } catch (err) {
        logger.warn("[notifyTeamJoinRequested] failed to load team doc", {
          requestId,
          teamId,
          err,
        });
      }
    }

    if (!ownerUid) {
      logger.warn(
        "[notifyTeamJoinRequested] missing ownerUid/ownerId after fallback",
        {
          requestId,
          teamId,
          req,
        }
      );
      return;
    }

    const requesterUid =
      (req.requesterUid as string | undefined) ??
      (req.uid as string | undefined) ??
      "";

    let requesterName =
      (req.requesterName as string | undefined) ??
      (req.username as string | undefined) ??
      "";

    if (!requesterName && requesterUid) {
      try {
        const userSnap = await db.collection("users").doc(requesterUid).get();
        const u = userSnap.data() || {};
        requesterName =
          (u.username as string | undefined) ??
          (u.displayName as string | undefined) ??
          "A player";
      } catch (err) {
        logger.warn("[notifyTeamJoinRequested] failed to load user profile", {
          requesterUid,
          err,
        });
        requesterName = "A player";
      }
    }

    const title = `Join request for ${teamName || "your squad"}`;
    const body = `${requesterName || "A player"} wants to join your squad.`;

    const tokens: string[] = [];
    try {
      const snap = await db
        .collection("users")
        .doc(ownerUid)
        .collection("tokens")
        .get();

      snap.docs.forEach((doc) => {
        const t = doc.id;
        if (t && typeof t === "string") tokens.push(t);
      });
    } catch (err) {
      logger.error("[notifyTeamJoinRequested] error fetching tokens", {
        ownerUid,
        err,
      });
      return;
    }

    if (!tokens.length) {
      logger.info("[notifyTeamJoinRequested] no tokens for owner, skipping", {
        ownerUid,
      });
      return;
    }

    const payload: admin.messaging.MulticastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: {
        type: "team_join_requested",
        requestId,
        teamId,
        teamName,
        requesterName,
      },
      android: {
        priority: "high",
      },
    };

    try {
      const resp = await messaging.sendEachForMulticast(payload);
      logger.info("[notifyTeamJoinRequested] sent", {
        requestId,
        ownerUid,
        teamId,
        teamName,
        success: resp.successCount,
        failure: resp.failureCount,
      });
    } catch (err) {
      logger.error("[notifyTeamJoinRequested] sendEachForMulticast failed", {
        err,
      });
    }
  }
);

/** ---------- team deleted ---------- */
export const notifyTeamDeleted = onDocumentDeleted(
  "teams/{teamId}",
  async (event) => {
    const teamId = event.params.teamId;
    const before = event.data?.data() as any | undefined;

    if (!before) {
      logger.warn("[notifyTeamDeleted] no before data", { teamId });
      return;
    }

    const teamName = before.name ?? "a squad";
    const ownerUid = before.ownerUid ?? "";
    const memberUids: string[] = Array.isArray(before.memberUids)
      ? before.memberUids.filter((u: any) => typeof u === "string")
      : [];

    const recipients = memberUids.filter((uid) => uid && uid !== ownerUid);

    if (!recipients.length) {
      logger.info("[notifyTeamDeleted] no recipients to notify", {
        teamId,
        memberCount: memberUids.length,
      });
      return;
    }

    const tokens: string[] = [];
    try {
      await Promise.all(
        recipients.map(async (uid) => {
          const snap = await db
            .collection("users")
            .doc(uid)
            .collection("tokens")
            .get();
          snap.docs.forEach((doc) => {
            if (typeof doc.id === "string") tokens.push(doc.id);
          });
        })
      );
    } catch (err) {
      logger.error("[notifyTeamDeleted] error fetching tokens", { err });
      return;
    }

    if (!tokens.length) {
      logger.info("[notifyTeamDeleted] no tokens found, nothing to send");
      return;
    }

    const title = `Squad deleted: ${teamName}`;
    const body = "This squad was deleted by its owner.";

    const msg: admin.messaging.MulticastMessage = {
      tokens,
      notification: { title, body },
      data: {
        type: "team_deleted",
        teamId,
        teamName,
      },
      android: { priority: "high" },
    };

    try {
      const resp = await messaging.sendEachForMulticast(msg);
      logger.info("[notifyTeamDeleted] sent squad deletion notice", {
        teamId,
        teamName,
        tokensSent: resp.successCount,
      });
    } catch (err) {
      logger.error("[notifyTeamDeleted] multicast send failed", { err });
    }
  }
);
