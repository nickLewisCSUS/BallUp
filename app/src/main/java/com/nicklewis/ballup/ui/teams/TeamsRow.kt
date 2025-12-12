package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicklewis.ballup.data.TeamsRepository
import com.nicklewis.ballup.model.Team
import com.nicklewis.ballup.model.UserProfile

// ---------- ROWS (cards) ----------

@Composable
fun SquadRow(
    team: Team,
    isOwner: Boolean,
    onViewMembers: (Team) -> Unit,
    onEdit: (Team) -> Unit,
    onDelete: (Team) -> Unit,
    onLeave: (Team) -> Unit,
    onViewRequests: (Team) -> Unit,
    onInvite: (Team) -> Unit,
    currentUid: String
) {
    val isMemberButNotOwner = !isOwner && team.memberUids.contains(currentUid)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            // ✅ lighter than surfaceVariant
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium)

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    team.preferredSkillLevel?.let {
                        Text(
                            "Skill: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.playDays.isNotEmpty()) {
                        Text(
                            "Days: ${team.playDays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.inviteOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Private squad (host invites only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    when {
                        isOwner -> Text(
                            "You’re the owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        isMemberButNotOwner -> Text(
                            "You’re in this squad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOwner) {
                        Row {
                            IconButton(onClick = { onEdit(team) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit squad")
                            }
                            IconButton(onClick = { onDelete(team) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete squad")
                            }
                        }
                        OutlinedButton(onClick = { onViewRequests(team) }) {
                            Text("View requests")
                        }
                        OutlinedButton(onClick = { onInvite(team) }) {
                            Text("Invite players")
                        }
                    } else if (isMemberButNotOwner) {
                        OutlinedButton(onClick = { onLeave(team) }) {
                            Text("Leave")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { onViewMembers(team) }) {
                Text("View members")
            }
        }
    }
}

@Composable
fun DiscoverSquadRow(
    team: Team,
    isOwner: Boolean,
    isMember: Boolean,
    hasRequested: Boolean,
    onRequestJoin: (Team) -> Unit,
    onCancelRequest: (Team) -> Unit,
    onViewMembers: (Team) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            // ✅ lighter card
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // (rest unchanged)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { onViewMembers(team) }) {
                Text("View members")
            }
        }
    }
}

@Composable
fun InviteRow(
    invite: TeamsRepository.TeamInviteForUser,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            // ✅ lighter card
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(invite.teamName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "You’ve been invited to join this squad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(onClick = onDecline) { Text("Decline") }
                Button(onClick = onAccept) { Text("Accept") }
            }
        }
    }
}

@Composable
fun MemberRow(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            profile.username.ifBlank { profile.displayName ?: "Unnamed player" },
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Skill: ${profile.skillLevel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
