package com.yin.cpufreq2.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yin.cpufreq2.R
import com.yin.cpufreq2.core.locale.ConfigManager
import com.yin.cpufreq2.core.locale.CpuFloatManager
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {
    Box (modifier = Modifier.padding(12.dp, 0.dp)) {
        Column (
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            ScaleChooseUnit()
            Spacer(modifier = Modifier.height(24.dp))
            HeightChooseUnit()
            Spacer(modifier = Modifier.height(24.dp))
            RotationChooseUnit()
            Spacer(modifier = Modifier.height(24.dp))
            ReverseOrderChooseUnit()
            Spacer(modifier = Modifier.height(24.dp))
            ControlPanel({ CpuFloatManager.start() }, { CpuFloatManager.stop() })
        }
    }
}

@Composable
private fun HeightChooseUnit() {
    var selectedMaxHeight by remember { mutableStateOf(ConfigManager.getMaxHeight()) }
    val minHeight = 40f
    val maxHeight = 360f
    val step = 40f
    val steps = ((maxHeight - minHeight) / step).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.max_height_title), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.current_height_format, selectedMaxHeight.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )

            Slider(
                value = selectedMaxHeight,
                onValueChange = { newValue ->
                    val steppedValue = (newValue / step).roundToInt() * step
                    val clampedValue = steppedValue.coerceIn(minHeight, maxHeight)
                    selectedMaxHeight = clampedValue
                },
                onValueChangeFinished = {
                    ConfigManager.saveMaxHeight(selectedMaxHeight)
                    CpuFloatManager.update()
                },
                valueRange = minHeight..maxHeight,
                steps = steps
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.min_height_label), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.max_height_label), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScaleChooseUnit() {
    var scaleValue by remember { mutableStateOf(ConfigManager.getScale()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.scale_title), fontWeight = FontWeight.Bold)
            Slider(
                value = scaleValue,
                onValueChange = { newScale ->
                    scaleValue = newScale
                    ConfigManager.saveScale(newScale)
                    CpuFloatManager.update()
                },
                valueRange = ConfigManager.MIN_SCALE..ConfigManager.MAX_SCALE,
                steps = 26,
                modifier = Modifier.fillMaxWidth()
            )
            Text("${(scaleValue * 100).toInt()}%", modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun RotationChooseUnit() {
    var selectedRotation by remember { mutableStateOf(ConfigManager.getRotation()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.rotation_title), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 90, 180, 270).forEach { angle ->
                    FilterChip(
                        selected = selectedRotation == angle,
                        onClick = {
                            selectedRotation = angle
                            ConfigManager.saveRotation(angle)
                            CpuFloatManager.update()
                        },
                        label = { Text("${angle}°") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReverseOrderChooseUnit() {
    var reverseOrder by remember { mutableStateOf(ConfigManager.isReverseOrder()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reverse_order_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(
                checked = reverseOrder,
                onCheckedChange = {
                    reverseOrder = it
                    ConfigManager.saveReverseOrder(it)
                    CpuFloatManager.update()
                }
            )
        }
    }
}

@Composable
fun ControlPanel(onStartClick: () -> Unit, onStopClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlButton(
            text = stringResource(R.string.start_button),
            icon = Icons.Default.PlayArrow,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onClick = onStartClick
        )
        ControlButton(
            text = stringResource(R.string.stop_button),
            icon = Icons.Default.Delete,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onStopClick
        )
    }
}

@Composable
fun ControlButton(text: String, icon: ImageVector, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}