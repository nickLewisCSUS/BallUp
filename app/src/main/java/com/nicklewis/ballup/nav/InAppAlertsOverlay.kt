// InAppAlertsOverlay.kt
package com.nicklewis.ballup.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@Composable
fun InAppAlertsOverlay(nav: NavHostController) {
    val snack = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    LaunchedEffect(Unit) {
        NotifBus.events.collect { evt ->
            when (evt) {
                is InAppAlert.RunSpots -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.subtitle}",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("run/${evt.runId}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is InAppAlert.RunCreated -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.courtName} • starts ${evt.timeText}",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("run/${evt.runId}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is InAppAlert.RunUpcoming -> {
                    val mins = evt.minutes
                    val timeText = when (mins) {
                        60 -> "in 1 hour"
                        10 -> "in 10 minutes"
                        else -> "soon"
                    }

                    val res = snack.showSnackbar(
                        message = "${evt.title} · starts $timeText at ${evt.courtName}",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("run/${evt.runId}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is InAppAlert.RunCancelled -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · Tap to view details",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("run/${evt.runId}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // invite sent *to you* (player)
                is InAppAlert.TeamInvite -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · Check your invites tab",
                        actionLabel = "Open",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("teams") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // your invite got accepted (owner view)
                is InAppAlert.TeamInviteAccepted -> {
                    val res = snack.showSnackbar(
                        message = evt.message,
                        actionLabel = "Open",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("teams") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // someone requested to join your squad (owner view)
                is InAppAlert.TeamJoinRequest -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.requesterName}",
                        actionLabel = "Review",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("teams") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // 🔹 NEW: your own join request was approved (player view)
                is InAppAlert.TeamJoinApproved -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.message}",
                        actionLabel = "Open",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("teams") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is InAppAlert.TeamDeleted -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.message}",
                        actionLabel = "Teams",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("teams") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is InAppAlert.RunInvite -> {
                    val res = snack.showSnackbar(
                        message = "${evt.title} · ${evt.courtName}",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        nav.navigate("run/${evt.runId}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
}
