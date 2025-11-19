package com.nicklewis.ballup.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val username: String? = null,      // new
    val skillLevel: String? = null,    // new
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // --- existing auth state ---
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- NEW: navigation events for login flow ---
    sealed class AuthNav {
        object ToHome : AuthNav()
        data class ToProfileSetup(
            val uid: String,
            val displayName: String?,
            val photoUrl: String?
        ) : AuthNav()
    }

    private val _navEvents = MutableSharedFlow<AuthNav>()
    val navEvents: SharedFlow<AuthNav> = _navEvents.asSharedFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(listener)
    }

    fun signInWithCredential(credential: AuthCredential) {
        _isLoading.value = true
        _errorMessage.value = null

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false

                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        _currentUser.value = user
                        handleUserAfterSignIn(user)
                    }
                } else {
                    _errorMessage.value =
                        task.exception?.localizedMessage ?: "Sign-in failed"
                }
            }
    }

    /**
     * Called only after sign-in succeeds.
     * Checks if there is already a username in users/{uid}.
     * - If yes  -> send ToHome
     * - If no   -> send ToProfileSetup
     */
    private fun handleUserAfterSignIn(user: FirebaseUser) {
        Log.d("AUTH_FLOW", "handleUserAfterSignIn uid=${user.uid}, email=${user.email}")
        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingProfile = snapshot.toObject<UserProfile>()
                val hasUsername = existingProfile?.username?.isNotBlank() == true

                viewModelScope.launch {
                    if (hasUsername) {
                        // Returning user: optional small update of display name / photo
                        upsertBasicProfile(user)
                        _navEvents.emit(AuthNav.ToHome)
                    } else {
                        // First time OR old user with no profile info yet
                        _navEvents.emit(
                            AuthNav.ToProfileSetup(
                                uid = user.uid,
                                displayName = user.displayName,
                                photoUrl = user.photoUrl?.toString()
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                _errorMessage.value =
                    e.localizedMessage ?: "Failed to check existing profile"
            }
    }

    /**
     * Writes basic info (no username/skill) using merge so we don't
     * wipe out profile fields set by the ProfileSetupScreen.
     */
    private fun upsertBasicProfile(user: FirebaseUser) {
        val now = Timestamp.now()
        val data = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl?.toString(),
            "updatedAt" to now
        )

        db.collection("users")
            .document(user.uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    fun signOut() {
        auth.signOut()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }

    fun checkExistingUserAndRoute() {
        val user = auth.currentUser
        // Only auto-route if it's a real provider user (has email)
        if (user != null && !user.email.isNullOrBlank()) {
            handleUserAfterSignIn(user)
        }
    }


}
