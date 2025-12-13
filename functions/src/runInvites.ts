// functions/src/runInvites.ts
import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

/**
 * When a run is created and has allowedUids (from squad selection),
 * fan out per-player runInvites so they get notifications
 * and see them in their inbox.
 */
export const createRunInvitesForNewRun = onDocumentCreated(
  "runs/{runId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const run = snap.data() as any;
    const runId = event.params.runId;

    // 👇 We don't care about access type here – any run with allowedUids gets invites
    const allowed = Array.isArray(run.allowedUids)
      ? (run.allowedUids as string[])
      : [];

    if (!allowed.length) {
      logger.info("[createRunInvitesForNewRun] no allowedUids, skipping", {
        runId,
      });
      return;
    }

    const hostId =
      (run.hostId as string | undefined) ??
      (run.ownerUid as string | undefined) ??
      "";

    const runName = (run.name as string | undefined) ?? "Pickup run";
    const courtId = (run.courtId as string | undefined) ?? "";

    let courtName = (run.courtName as string | undefined) ?? "";
    if (!courtName && courtId) {
      try {
        const courtSnap = await admin
          .firestore()
          .collection("courts")
          .doc(courtId)
          .get();
        courtName = (courtSnap.data()?.name as string | undefined) ?? "";
      } catch (err) {
        logger.warn("[createRunInvitesForNewRun] failed to load court name", {
          runId,
          courtId,
          err,
        });
      }
    }

    const db = admin.firestore();
    const batch = db.batch();
    const now = admin.firestore.FieldValue.serverTimestamp();

    let count = 0;

    allowed.forEach((uid) => {
      // don’t send an invite to the host themselves
      if (!uid || uid === hostId) return;

      // ✅ deterministic ID prevents duplicate spam across create + later invites
      const ref = db.collection("runInvites").doc(`${runId}_${uid}`);

      batch.set(
        ref,
        {
          inviteeUid: uid,
          runId,
          runName,
          courtId,
          courtName,
          status: "pending",
          createdAt: now,
        },
        { merge: true } // if it exists, don't fail the batch
      );

      count++;
    });

    if (!count) {
      logger.info(
        "[createRunInvitesForNewRun] no non-host allowed uids, skipping",
        { runId }
      );
      return;
    }

    await batch.commit();
    logger.info("[createRunInvitesForNewRun] created invites", {
      runId,
      count,
    });
  }
);

/**
 * ✅ NEW:
 * When allowedUids changes on an existing run (RunDetailsScreen "Invite squad"),
 * create runInvites only for the *newly added* uids.
 */
export const createRunInvitesOnAllowedUidsAdded = onDocumentUpdated(
  "runs/{runId}",
  async (event) => {
    const before = event.data?.before?.data() as any;
    const after = event.data?.after?.data() as any;
    const runId = event.params.runId;

    if (!before || !after) return;

    const beforeAllowed = Array.isArray(before.allowedUids)
      ? (before.allowedUids as string[])
      : [];
    const afterAllowed = Array.isArray(after.allowedUids)
      ? (after.allowedUids as string[])
      : [];

    // Find newly-added uids
    const beforeSet = new Set<string>(beforeAllowed);
    const added = afterAllowed.filter((uid) => uid && !beforeSet.has(uid));

    if (!added.length) {
      // no new invites added
      return;
    }

    const hostId =
      (after.hostId as string | undefined) ??
      (after.ownerUid as string | undefined) ??
      "";

    const runName = (after.name as string | undefined) ?? "Pickup run";
    const courtId = (after.courtId as string | undefined) ?? "";

    let courtName = (after.courtName as string | undefined) ?? "";
    if (!courtName && courtId) {
      try {
        const courtSnap = await admin.firestore().collection("courts").doc(courtId).get();
        courtName = (courtSnap.data()?.name as string | undefined) ?? "";
      } catch (err) {
        logger.warn("[createRunInvitesOnAllowedUidsAdded] failed to load court name", {
          runId,
          courtId,
          err,
        });
      }
    }

    const db = admin.firestore();
    const batch = db.batch();
    const now = admin.firestore.FieldValue.serverTimestamp();

    let count = 0;

    for (const uid of added) {
      if (!uid || uid === hostId) continue;

      // ✅ deterministic ID so re-invites don't spam notifications
      const ref = db.collection("runInvites").doc(`${runId}_${uid}`);

      batch.set(
        ref,
        {
          inviteeUid: uid,
          runId,
          runName,
          courtId,
          courtName,
          status: "pending",
          createdAt: now,
        },
        { merge: true }
      );

      count++;
    }

    if (!count) return;

    await batch.commit();
    logger.info("[createRunInvitesOnAllowedUidsAdded] created invites", { runId, count });
  }
);

/**
 * When a runInvites doc is created, send a push notification
 * directly to that invited player (per-user FCM).
 */
export const notifyRunInviteCreated = onDocumentCreated(
  "runInvites/{inviteId}",
  async (event) => {
    const inviteId = event.params.inviteId;
    const invite = event.data?.data() as any;

    if (!invite) {
      logger.warn("[notifyRunInviteCreated] no invite data", { inviteId });
      return;
    }

    const uid = (invite.inviteeUid as string | undefined) ?? "";
    if (!uid) {
      logger.warn("[notifyRunInviteCreated] invite missing inviteeUid", {
        inviteId,
        invite,
      });
      return;
    }

    const runId = (invite.runId as string | undefined) ?? "";
    const runName = (invite.runName as string | undefined) ?? "Pickup run";
    const courtName = (invite.courtName as string | undefined) ?? "a court";

    const db = admin.firestore();
    const tokensSnap = await db
      .collection("users")
      .doc(uid)
      .collection("tokens")
      .get();

    if (tokensSnap.empty) {
      logger.info("[notifyRunInviteCreated] no tokens for user, skipping", {
        uid,
        inviteId,
      });
      return;
    }

    const tokens = tokensSnap.docs.map((d) => d.id as string);

    const title = `Run invite: ${runName}`;
    const body = `You were invited to a run at ${courtName}.`;

    const msg: admin.messaging.MulticastMessage = {
      tokens,
      notification: { title, body },
      data: {
        type: "run_invite",
        inviteId,
        runId,
        runName,
        courtName,
      },
      android: { priority: "high", notification: { channelId: "runs" } },
    };

    try {
      const resp = await admin.messaging().sendEachForMulticast(msg);
      logger.info("[notifyRunInviteCreated] sent run invite notif", {
        inviteId,
        uid,
        runId,
        runName,
        courtName,
        success: resp.successCount,
        failure: resp.failureCount,
      });
    } catch (err) {
      logger.error("[notifyRunInviteCreated] multicast send failed", { err });
    }
  }
);

export const deleteRunInvitesOnAllowedUidsRemoved = onDocumentUpdated(
  "runs/{runId}",
  async (event) => {
    const before = event.data?.before?.data() as any;
    const after  = event.data?.after?.data() as any;
    const runId  = event.params.runId;
    if (!before || !after) return;

    const b = Array.isArray(before.allowedUids) ? before.allowedUids : [];
    const a = Array.isArray(after.allowedUids)  ? after.allowedUids  : [];

    const afterSet = new Set(a);
    const removed = b.filter((uid: string) => uid && !afterSet.has(uid));
    if (!removed.length) return;

    const db = admin.firestore();
    const batch = db.batch();
    removed.forEach((uid: string) => {
      batch.delete(db.collection("runInvites").doc(`${runId}_${uid}`));
    });

    await batch.commit();
    logger.info("[deleteRunInvitesOnAllowedUidsRemoved] deleted", { runId, count: removed.length });
  }
);

