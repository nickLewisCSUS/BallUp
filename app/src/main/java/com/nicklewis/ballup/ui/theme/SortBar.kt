package com.nicklewis.ballup.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicklewis.ballup.util.SortMode

@Composable
fun SortBar(
    sortMode: SortMode,
    onChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = sortMode == SortMode.CLOSEST,      onClick = { onChange(SortMode.CLOSEST) },      label = { Text("Closest") })
        FilterChip(selected = sortMode == SortMode.MOST_PLAYERS, onClick = { onChange(SortMode.MOST_PLAYERS) }, label = { Text("Most players") })
        FilterChip(selected = sortMode == SortMode.NEWEST,       onClick = { onChange(SortMode.NEWEST) },       label = { Text("Newest") })
    }
}