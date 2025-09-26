package com.nicklewis.ballup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicklewis.ballup.data.CourtLite
import com.nicklewis.ballup.data.StarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class StarsViewModel(
    private val repo: StarRepository = StarRepository()
) : ViewModel() {

    private val _starred = MutableStateFlow<Set<String>>(emptySet())
    val starred: StateFlow<Set<String>> = _starred.asStateFlow()

    init {
        viewModelScope.launch {
            repo.starredIds().collect { _starred.value = it }
        }
    }

    fun toggle(court: CourtLite, newValue: Boolean, runAlertsEnabled: Boolean) {
        val before = _starred.value
        _starred.value = if (newValue) before + court.id else before - court.id

        viewModelScope.launch {
            try {
                repo.setStar(court, newValue)
                if (runAlertsEnabled) {
                    try {
                        val topic = "court_${court.id}"
                        if (newValue) {
                            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
                        } else {
                            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
                        }
                    } catch (_: Exception) {
                        // non-fatal
                    }
                }
            } catch (_: Exception) {
                _starred.value = before
            }
        }
    }



    private suspend fun setTopicSubscription(courtId: String, subscribe: Boolean) {
        val token = FirebaseMessaging.getInstance().token.await()
        val fn = Firebase.functions.getHttpsCallable("setCourtTopicSubscription")
        fn.call(
            mapOf(
                "token" to token,
                "courtId" to courtId,
                "subscribe" to subscribe
            )
        ).await()
    }
}
