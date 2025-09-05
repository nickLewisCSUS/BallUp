import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2/options";

admin.initializeApp();

// Optional but nice for cost/latency control:
setGlobalOptions({ region: "us-central1", maxInstances: 1 });

export { cleanupStaleRuns } from "./cleanupRuns";