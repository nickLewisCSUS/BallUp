package com.nicklewis.ballup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                Log.d("Auth", "Signed in as ${result.user?.uid}")

                val db = FirebaseFirestore.getInstance()
                val testDoc = mapOf(
                    "message" to "Hello Firebase!",
                    "uid" to result.user?.uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                db.collection("test")
                    .add(testDoc)
                    .addOnSuccessListener { docRef ->
                        Log.d("FirebaseTest", "Document added with ID: ${docRef.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.w("FirebaseTest", "Error adding document", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Auth", "Anonymous sign-in failed", e)
            }

        setContent {
            // your Compose UI
        }
    }
}
