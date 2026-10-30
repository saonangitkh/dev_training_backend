package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** − quantity + . The caller decides the limits through [canDecrease] / [canIncrease]. */
@Composable
fun QuantityStepper(
    quantity: Int,
    max: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = { onQuantityChange(quantity - 1) }, enabled = canDecrease) { Text("−") }
        Text("$quantity", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onQuantityChange(quantity + 1) }, enabled = canIncrease) { Text("+") }
        Text("max $max", style = MaterialTheme.typography.bodySmall)
    }
}
