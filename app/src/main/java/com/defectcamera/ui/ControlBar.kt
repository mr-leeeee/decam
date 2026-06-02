package com.defectcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraControlBar(
    ev: Float, onEvChange: (Float) -> Unit,
    isManualFocus: Boolean, onFocusToggle: () -> Unit, onManualFocusChange: (Float) -> Unit,
    isoValue: Int, onIsoChange: (Int) -> Unit,
    ssNs: Long, onSsChange: (Long) -> Unit,
    onShutterClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    var showIso by remember { mutableStateOf(false) }
    var showSs by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        // EV + AF/MF
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
            Slider(value = ev, onValueChange = onEvChange, valueRange = -2f..2f, steps = 19,
                modifier = Modifier.weight(1f).height(24.dp),
                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
            Text("%+.1f".format(ev), color = Color.White, fontSize = 11.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (isManualFocus) Color(0xFFFF3D00).copy(alpha = 0.2f) else Color.Transparent).border(1.5.dp, if (isManualFocus) Color(0xFFFF3D00) else Color.Gray, CircleShape).clickable(onClick = onFocusToggle), contentAlignment = Alignment.Center) {
                Text(if (isManualFocus) "MF" else "AF", color = if (isManualFocus) Color(0xFFFF3D00) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // MF focus slider
        if (isManualFocus) {
            var fd by remember { mutableFloatStateOf(0.2f) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("초점", color = Color(0xFFFF3D00), fontSize = 10.sp, modifier = Modifier.width(28.dp))
                Slider(value = fd, onValueChange = { fd = it; onManualFocusChange(it) }, valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
                Text("%.0fcm".format(if (fd > 0.01f) 100f / fd else 999f), color = Color.White, fontSize = 10.sp)
            }
        }

        // Bottom row: ISO | Shutter (center) | Gallery + SS
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ISO tap area
            Row(Modifier.weight(1f).clickable { showIso = !showIso; showSs = false }, verticalAlignment = Alignment.CenterVertically) {
                Text("ISO", color = Color.Gray, fontSize = 10.sp)
                Spacer(Modifier.width(2.dp))
                Text(if (isoValue > 0) isoValue.toString() else "Auto", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            }

            // Shutter button (center)
            Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White).clickable(onClick = onShutterClick), contentAlignment = Alignment.Center) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFFF3D00)))
            }

            // SS + Gallery (right)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // SS tap area
                Row(Modifier.clickable { showSs = !showSs; showIso = false }, verticalAlignment = Alignment.CenterVertically) {
                    Text("SS", color = Color.Gray, fontSize = 10.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(when { ssNs == 0L -> "Auto"; else -> "1/${1_000_000_000L / ssNs}" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(4.dp))
                // Gallery button
                IconButton(onClick = onGalleryClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (showIso) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(isoValue.toString(), color = Color(0xFFFF3D00), fontSize = 11.sp, modifier = Modifier.width(40.dp))
                Slider(value = isoValue.toFloat(), onValueChange = { onIsoChange(it.toInt()) }, valueRange = 50f..3200f, steps = 62, modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
                if (isoValue > 0) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF3D00)).clickable { onIsoChange(0) }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("A", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showSs) {
            Spacer(Modifier.height(4.dp))
            val presets = listOf(0L to "Auto", 33_333_333L to "1/30", 16_666_666L to "1/60", 8_333_333L to "1/120", 4_000_000L to "1/250", 2_000_000L to "1/500", 1_000_000L to "1/1000", 500_000L to "1/2000")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                presets.forEach { (ns, lbl) ->
                    val sel = if (ns == 0L) ssNs == 0L else ssNs == ns
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (sel) Color(0xFFFF3D00) else Color.Gray.copy(alpha = 0.3f)).clickable { onSsChange(ns) }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(lbl, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
