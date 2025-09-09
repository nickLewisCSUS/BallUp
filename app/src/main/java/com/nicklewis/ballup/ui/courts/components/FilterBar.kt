package com.nicklewis.ballup.ui.courts.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nicklewis.ballup.ui.theme.FilterMenu
import com.nicklewis.ballup.util.SortMode

@Composable
fun FilterBar(
    showIndoor: Boolean,
    showOutdoor: Boolean,
    sortMode: SortMode,
    onToggleIndoor: () -> Unit,
    onToggleOutdoor: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterMenu(
        showIndoor = showIndoor,
        showOutdoor = showOutdoor,
        sortMode = sortMode,
        onToggleIndoor = onToggleIndoor,
        onToggleOutdoor = onToggleOutdoor,
        onSortChange = onSortChange,
        modifier = modifier
    )
}
