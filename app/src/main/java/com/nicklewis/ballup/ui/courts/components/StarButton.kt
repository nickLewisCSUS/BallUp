package com.nicklewis.ballup.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable

@Composable
fun StarButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    IconToggleButton(checked = checked, onCheckedChange = onCheckedChange) {
        Icon(
            imageVector = if (checked) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (checked) "Unstar court" else "Star court"
        )
    }
}
