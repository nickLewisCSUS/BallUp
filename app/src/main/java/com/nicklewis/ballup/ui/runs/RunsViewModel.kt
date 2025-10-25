package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.nicklewis.ballup.model.Run

class RunsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // add this new method
    fun createRunFromDialog(r: Run) {
        val uid = auth.currentUser?.uid ?: return

        val doc = hashMapOf(
            "courtId"     to r.courtId,
            "status"      to "active",
            "startsAt"    to r.startsAt,
            "endsAt"      to r.endsAt,
            "hostId"      to uid,
            "mode"        to r.mode,
            "maxPlayers"  to r.maxPlayers,
            "playerIds"   to listOf(uid),       // creator auto-joins
            "playerCount" to 1,
            "createdAt"   to FieldValue.serverTimestamp()
        )

        // optional: for backward compatibility
        doc["startTime"] = r.startsAt

        db.collection("runs")
            .add(doc)
            .addOnSuccessListener { Log.d("RunsVM", "Run created successfully!") }
            .addOnFailureListener { e -> Log.e("RunsVM", "createRun failed", e) }
    }
}
