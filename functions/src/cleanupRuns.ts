import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";

/** Runs every 15 min; ends runs stale for ≥60 min */
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
