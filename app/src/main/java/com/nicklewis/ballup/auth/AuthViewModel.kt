package com.nicklewis.ballup.auth

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.AuthCredential
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null
)

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
                        upsertUserProfile(user)
                    }
                } else {
                    _errorMessage.value =
                        task.exception?.localizedMessage ?: "Sign-in failed"
                }
            }
    }

    private fun upsertUserProfile(user: FirebaseUser) {
        val profile = UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            photoUrl = user.photoUrl?.toString(),
            createdAt = Timestamp.now()
        )

        db.collection("users")
            .document(user.uid)
            .set(profile, com.google.firebase.firestore.SetOptions.merge())
    }

    fun signOut() {
        auth.signOut()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }
}
