package com.nicklewis.ballup.ui.runs

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.nicklewis.ballup.model.Run

class RunsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    fun createRunFromDialog(r: Run) {
        val uid = auth.currentUser?.uid ?: return

        val name = r.name.trim().replace(Regex("\\s+"), " ")
        val doc = hashMapOf(
            "courtId"     to r.courtId,
            "status"      to "active",
            "startsAt"    to r.startsAt,
            "endsAt"      to r.endsAt,
            "hostId"      to uid,
            "mode"        to r.mode,
            "maxPlayers"  to r.maxPlayers,
            "playerIds"   to listOf(uid),
            "playerCount" to 1,
            "createdAt"   to FieldValue.serverTimestamp(),
            "name"        to name,
            "name_lc"     to name.lowercase(),           // handy for simple search
            "nameTokens"  to name.lowercase().split(" ").filter { it.isNotBlank() } // optional
        )
        doc["startTime"] = r.startsAt // legacy
        db.collection("runs")
            .add(doc)
            .addOnSuccessListener { Log.d("RunsVM", "Run created successfully!") }
            .addOnFailureListener { e -> Log.e("RunsVM", "createRun failed", e) }
    }
}
