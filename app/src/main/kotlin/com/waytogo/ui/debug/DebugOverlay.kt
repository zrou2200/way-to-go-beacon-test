package com.waytogo.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waytogo.core.engine.DebugSnapshot

/** Diagnostics panel (Section 7.2). */
@Composable
fun DebugOverlay(snapshot: DebugSnapshot, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6000000)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "method=${snapshot.method ?: "-"}  acc=${snapshot.accuracyM?.let { "%.1fm".format(it) } ?: "-"}  " +
                    "used=${snapshot.beaconsUsed}  floor=${snapshot.resolvedFloor ?: "-"}  " +
                    "rate=%.1f/s".format(snapshot.scanRateHz),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
            Divider(Modifier.padding(vertical = 6.dp), color = Color(0x33FFFFFF))
            Text(
                text = "label        rssi  n   dist  topK",
                color = Color(0xFFB0BEC5),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                contentPadding = PaddingValues(top = 4.dp),
            ) {
                items(snapshot.heard) { obs ->
                    val inTopK = obs.beacon.key in snapshot.usedKeys
                    Text(
                        text = "%-12s %5.0f %2d %5.1f  %s".format(
                            obs.beacon.label.take(12),
                            obs.filteredRssi,
                            obs.sampleCount,
                            obs.distanceM,
                            if (inTopK) "yes" else "",
                        ),
                        color = if (inTopK) Color(0xFF81C784) else Color.White,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
