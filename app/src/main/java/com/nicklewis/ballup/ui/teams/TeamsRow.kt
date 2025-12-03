package com.nicklewis.ballup.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                // LEFT: details
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (team.preferredSkillLevel != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Skill: ${team.preferredSkillLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.playDays.isNotEmpty()) {
                        Text(
                            text = "Days: ${team.playDays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (team.inviteOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Private squad (host invites only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    when {
                        isOwner -> {
                            Text(
                                text = "You’re the owner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        isMemberButNotOwner -> {
                            Text(
                                text = "You’re in this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT: actions stacked so they don't squeeze the content
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOwner) {
                        Row {
                            IconButton(onClick = { onEdit(team) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit squad"
                                )
                            }
                            IconButton(onClick = { onDelete(team) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete squad"
                                )
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                // LEFT: details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = team.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${team.memberUids.size} players",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (team.preferredSkillLevel != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Skill: ${team.preferredSkillLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (team.playDays.isNotEmpty()) {
                        Text(
                            text = "Days: ${team.playDays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (team.inviteOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Private squad (host invites only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    when {
                        isOwner -> {
                            Text(
                                text = "You own this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        isMember -> {
                            Text(
                                text = "Already in this squad",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        hasRequested -> {
                            Text(
                                text = "Request pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT: actions stacked
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        isOwner || isMember -> {
                            // no join actions
                        }

                        hasRequested -> {
                            OutlinedButton(onClick = { onCancelRequest(team) }) {
                                Text("Cancel")
                            }
                        }

                        else -> {
                            Button(onClick = { onRequestJoin(team) }) {
                                Text("Request")
                            }
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
fun InviteRow(
    invite: TeamsRepository.TeamInviteForUser,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = invite.teamName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You’ve been invited to join this squad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (invite.preferredSkillLevel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Skill: ${invite.preferredSkillLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (invite.playDays.isNotEmpty()) {
                Text(
                    text = "Days: ${invite.playDays.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (invite.inviteOnly) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Private squad (host invites only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(onClick = onDecline) {
                    Text("Decline")
                }
                Button(onClick = onAccept) {
                    Text("Accept")
                }
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
            text = profile.username.ifBlank { profile.displayName ?: "Unnamed player" },
            style = MaterialTheme.typography.bodyLarge
        )
        if (!profile.displayName.isNullOrBlank() &&
            profile.displayName != profile.username
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Skill: ${profile.skillLevel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
