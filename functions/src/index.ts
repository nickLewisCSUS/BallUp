import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";
import { onCall, HttpsError } from "firebase-functions/v2/https";

admin.initializeApp();

// Optional tuning
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

// keep your other exports
export { cleanupStaleRuns } from "./cleanupRuns";

// Callable: subscribe/unsubscribe a device token to a court topic
export const setCourtTopicSubscription = onCall(async (request) => {
  // v2: auth is on request.auth
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Login required");
  }

  // v2: data is on request.data
  const { token, courtId, subscribe } = (request.data ?? {}) as {
    token?: string;
    courtId?: string;
    subscribe?: boolean;
  };

  if (!token || !courtId) {
    throw new HttpsError("invalid-argument", "token and courtId required");
  }

  const topic = `court_${courtId}`;
  if (subscribe) {
    await admin.messaging().subscribeToTopic([token], topic);
  } else {
    await admin.messaging().unsubscribeFromTopic([token], topic);
  }

  return { ok: true };
});
