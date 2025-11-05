package com.nicklewis.ballup.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicklewis.ballup.util.SortMode

@Composable
fun FilterMenu(
    showIndoor: Boolean,
    showOutdoor: Boolean,
    sortMode: SortMode,
    onToggleIndoor: () -> Unit,
    onToggleOutdoor: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
    // NEW: starred filter support
    showStarredOnly: Boolean = false,
    onToggleStarredOnly: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
            label = {
                val sortLabel = when (sortMode) {
                    SortMode.CLOSEST -> "Closest"
                    SortMode.MOST_PLAYERS -> "Most players"
                    SortMode.NEWEST -> "Newest"
                }
                Text("Filters • $sortLabel")
            }
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Visibility toggles
            DropdownMenuItem(
                text = { Text("Indoor courts") },
                onClick = { onToggleIndoor() },
                leadingIcon = {
                    Checkbox(
                        checked = showIndoor,
                        onCheckedChange = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Outdoor courts") },
                onClick = { onToggleOutdoor() },
                leadingIcon = {
                    Checkbox(
                        checked = showOutdoor,
                        onCheckedChange = null
                    )
                }
            )

            // NEW: Starred courts only
            DropdownMenuItem(
                text = { Text("Starred courts only") },
                onClick = { onToggleStarredOnly() },
                leadingIcon = {
                    Checkbox(
                        checked = showStarredOnly,
                        onCheckedChange = null
                    )
                }
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Sort section
            DropdownMenuItem(
                text = { Text("Sort: Closest") },
                onClick = { onSortChange(SortMode.CLOSEST); expanded = false },
                trailingIcon = {
                    RadioButton(
                        selected = sortMode == SortMode.CLOSEST,
                        onClick = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Sort: Most players") },
                onClick = { onSortChange(SortMode.MOST_PLAYERS); expanded = false },
                trailingIcon = {
                    RadioButton(
                        selected = sortMode == SortMode.MOST_PLAYERS,
                        onClick = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Sort: Newest") },
                onClick = { onSortChange(SortMode.NEWEST); expanded = false },
                trailingIcon = {
                    RadioButton(
                        selected = sortMode == SortMode.NEWEST,
                        onClick = null
                    )
                }
            )
        }
    }
}
