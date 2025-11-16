import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";

// Existing function (keep this)
export const cleanupStaleRuns = onSchedule(
  { schedule: "every 15 minutes", timeZone: "America/Los_Angeles" },
  async () => {
    const db = admin.firestore();

    const cutoff = admin.firestore.Timestamp.fromDate(
      new Date(Date.now() - 60 * 60 * 1000) // 60 min
    );

    const qs = await db
      .collection("runs")
      .where("status", "==", "active")
      .where("lastHeartbeatAt", "<", cutoff)
      .get();

    if (qs.empty) return;

    const batch = db.batch();
    qs.forEach((d) => {
      batch.update(d.ref, {
        status: "ended",
        endedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });
    await batch.commit();
  }
);

// NEW: purge finished runs older than X days
export const purgeOldFinishedRuns = onSchedule(
  { schedule: "every 24 hours", timeZone: "America/Los_Angeles" },
  async () => {
    const db = admin.firestore();

    // How many days to keep finished runs
    const DAYS_TO_KEEP = 30;

    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - DAYS_TO_KEEP);
    const cutoff = admin.firestore.Timestamp.fromDate(cutoffDate);

    const batchSize = 300; // stay under 500 writes per batch

    // Helper: delete in batches for a given query
    async function deleteByQuery(q: FirebaseFirestore.Query) {
      let snap = await q.limit(batchSize).get();
      while (!snap.empty) {
        const batch = db.batch();
        snap.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();
        snap = await q.limit(batchSize).get();
      }
    }

    // 1) Auto-ended runs: use endedAt
    await deleteByQuery(
      db
        .collection("runs")
        .where("status", "==", "ended")
        .where("endedAt", "<", cutoff)
    );

    // 2) Host-cancelled runs: use endsAt (scheduled end time)
    await deleteByQuery(
      db
        .collection("runs")
        .where("status", "==", "cancelled")
        .where("endsAt", "<", cutoff)
    );
  }
);
