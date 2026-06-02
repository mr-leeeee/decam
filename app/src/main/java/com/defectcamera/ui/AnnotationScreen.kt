package com.defectcamera.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.defectcamera.data.PhotoRepository
import com.defectcamera.data.models.DefectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

data class DrawPath(val tool: String, val points: List<Pair<Float, Float>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    photoFile: File?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val photoRepo = remember { PhotoRepository(context) }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var cs by remember { mutableStateOf(IntSize.Zero) }
    var tool by remember { mutableStateOf("arrow") }
    var sw by remember { mutableFloatStateOf(6f) }
    var epos by remember { mutableStateOf(Offset.Zero) }
    var selectedColor by remember { mutableStateOf(android.graphics.Color.RED) }
    val colorPresets = listOf(
        android.graphics.Color.RED to "빨간색",
        android.graphics.Color.GREEN to "초록색",
        android.graphics.Color.BLUE to "파란색",
        android.graphics.Color.YELLOW to "노란색",
        android.graphics.Color.rgb(255, 0, 255) to "분홍색",
        android.graphics.Color.rgb(0, 255, 255) to "하늘색",
        android.graphics.Color.WHITE to "흰색",
        android.graphics.Color.rgb(255, 152, 0) to "주황색"
    )

    var curPath by remember { mutableStateOf<DrawPath?>(null) }
    var hist = remember { mutableStateListOf<Bitmap>() }
    val maxUndo = 20

    fun pushUndo() {
        overlay?.copy(Bitmap.Config.ARGB_8888, true)?.let { b ->
            while (hist.size >= maxUndo) hist.removeAt(0)
            hist.add(b)
        }
    }

    var showAnnotations by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(photoFile) {
        photo = withContext(Dispatchers.IO) { photoFile?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }

    fun bmp(w: Int, h: Int) {
        if (overlay == null || overlay!!.width != w || overlay!!.height != h)
            overlay = Bitmap.createBitmap(maxOf(w, 1), maxOf(h, 1), Bitmap.Config.ARGB_8888)
    }

    fun saveToGallery(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DefectCamera")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 100, out) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            }
        } catch (_: Exception) {}
    }

    fun commitShape(t: String, pts: List<Pair<Float, Float>>) {
        if (pts.size < 2) return
        val s = overlay ?: return; val r = s.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val c = Canvas(r); val sw2 = sw
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = sw2; color = selectedColor }
        val sx = pts[0].first; val sy = pts[0].second; val ex = pts[pts.size - 1].first; val ey = pts[pts.size - 1].second
        when (t) {
            "arrow" -> {
                c.drawLine(sx, sy, ex, ey, p)
                val dx = ex - sx; val dy = ey - sy
                if (sqrt(dx * dx + dy * dy) > 1f) {
                    val al = (sw2 * 6f).coerceAtLeast(20f); val ag = atan2(dy.toDouble(), dx.toDouble()); val aa = Math.toRadians(25.0)
                    val ap = Path().apply { moveTo(ex, ey); lineTo(ex - al * cos(ag - aa).toFloat(), ey - al * sin(ag - aa).toFloat()); moveTo(ex, ey); lineTo(ex - al * cos(ag + aa).toFloat(), ey - al * sin(ag + aa).toFloat()) }
                    Paint(p).apply { strokeWidth = sw2 * 1.5f }.let { c.drawPath(ap, it) }
                }
            }
            "circle" -> { c.drawOval(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey), p) }
            "rect" -> { c.drawRect(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey), p) }
            "pen" -> { val sp = Path(); sp.moveTo(pts[0].first, pts[0].second); for (i in 1 until pts.size) sp.lineTo(pts[i].first, pts[i].second); c.drawPath(sp, p) }
        }
        overlay = r
    }

    fun erase(x: Float, y: Float, r: Float) {
        val s = overlay ?: return; val c = s.copy(Bitmap.Config.ARGB_8888, true) ?: return
        Canvas(c).drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.FILL })
        overlay = c
    }
    fun eraseL(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        val s = overlay ?: return; val c = s.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = w }
        Canvas(c).drawLine(x1, y1, x2, y2, p); overlay = c
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("표시", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    IconButton(onClick = { showAnnotations = !showAnnotations }) { Icon(if (showAnnotations) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Sketch", tint = if (showAnnotations) Color(0xFFFF3D00) else Color.Gray) }
                    IconButton(onClick = { if (hist.isNotEmpty()) { overlay = hist.removeAt(hist.size - 1); curPath = null } }) { Icon(Icons.Default.Undo, "Undo", tint = Color(0xFFFF3D00)) }
                    IconButton(onClick = {
                        photo?.let { src ->
                            val w = src.width; val h = src.height
                            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val c = Canvas(result); c.drawBitmap(src, 0f, 0f, null)
                            overlay?.let { db ->
                                val sc = max(w.toFloat() / db.width, h.toFloat() / db.height)
                                val dw = (db.width * sc).toInt(); val dh = (db.height * sc).toInt(); val ox = (w - dw) / 2f; val oy = (h - dh) / 2f
                                c.drawBitmap(db, android.graphics.Rect(0, 0, db.width, db.height), android.graphics.Rect(ox.toInt(), oy.toInt(), ox.toInt() + dw, oy.toInt() + dh), null)
                            }
                            saveToGallery(result)
                            photoRepo.saveAnnotatedPhoto(result, photoFile!!, DefectType.SCRATCH) { onSaved() }
                        }
                    }) { Icon(Icons.Outlined.CheckCircle, "Save", tint = Color(0xFF4CAF50)) }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Default.Info, "Help", tint = Color.Gray) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(0.9f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("arrow" to "화살표", "circle" to "원", "rect" to "사각", "pen" to "펜", "eraser" to "지우개").forEach { (k, lbl) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (tool == k) Color(0xFFFF3D00).copy(0.3f) else Color.Transparent).clickable { tool = k }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(when (k) { "arrow" -> Icons.Default.ArrowForward; "circle" -> Icons.Default.Circle; "rect" -> Icons.Default.CropSquare; "pen" -> Icons.Default.Draw; else -> Icons.Default.AutoFixHigh }, lbl, tint = if (tool == k) Color(0xFFFF3D00) else Color.White, modifier = Modifier.size(20.dp))
                            Text(lbl, color = if (tool == k) Color(0xFFFF3D00) else Color.White.copy(0.7f), fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LineWeight, "W", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Slider(value = sw, onValueChange = { sw = it }, valueRange = 2f..30f, modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
                    Text("${sw.toInt()}px", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(26.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, "Z", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Slider(value = scale, onValueChange = { scale = it }, valueRange = 1f..10f, steps = 89, modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
                    Text("%.1fx".format(scale), color = Color.White, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    colorPresets.forEach { (c, _) ->
                        val isSel = c == selectedColor
                        Box(Modifier.size(if (isSel) 22.dp else 18.dp).clip(RoundedCornerShape(50)).background(Color(c)).border(if (isSel) 2.dp else 0.dp, Color.White, RoundedCornerShape(50)).clickable { selectedColor = c }.padding(0.dp))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            photo?.let { src ->
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                        .pointerInput(tool, showAnnotations) {
                            val pts = mutableListOf<Pair<Float, Float>>()
                            var pinching: Boolean
                            var lastDist: Float

                            awaitEachGesture {
                                val down = awaitFirstDown()
                                pinching = false; lastDist = 0f; pts.clear()
                                var lastCentroid: Offset? = null
                                var lastPan: Offset? = null
                                pts.add(down.position.x to down.position.y)
                                bmp(cs.width, cs.height)

                                if (showAnnotations) {
                                    if (tool == "eraser") { epos = down.position; erase(down.position.x, down.position.y, sw * 3f) }
                                    else { curPath = DrawPath(tool = tool, points = pts.toList()) }
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Move) {
                                        val changes = event.changes.filter { it.pressed }
                                        if (changes.size >= 2) {
                                            if (!pinching) { pinching = true; lastDist = 0f; lastCentroid = null }
                                            val centroid = Offset(
                                                changes.sumOf { it.position.x.toDouble() }.toFloat() / changes.size,
                                                changes.sumOf { it.position.y.toDouble() }.toFloat() / changes.size
                                            )
                                            val d = (changes[0].position - changes[1].position).getDistance()
                                            if (lastDist > 0f) {
                                                val ratio = d / lastDist
                                                val newS = (scale * ratio).coerceIn(1f, 10f)

                                                val r2 = newS / scale
                                                offsetX = centroid.x - r2 * (centroid.x - offsetX)
                                                offsetY = centroid.y - r2 * (centroid.y - offsetY)
                                                scale = newS
                                            }
                                            lastDist = d
                                            if (lastCentroid != null) {
                                                offsetX += centroid.x - lastCentroid.x
                                                offsetY += centroid.y - lastCentroid.y
                                            }
                                            lastCentroid = centroid
                                        } else if (!pinching) {
                                            val ch = changes.first()
                                            if (showAnnotations) {
                                                pts.add(ch.position.x to ch.position.y)
                                                if (tool == "eraser") {
                                                    epos = ch.position; val r = sw * 3f
                                                    if (pts.size >= 2) { val pp = pts[pts.size - 2]; eraseL(pp.first, pp.second, ch.position.x, ch.position.y, r) } else erase(ch.position.x, ch.position.y, r)
                                                } else {
                                                    curPath = DrawPath(tool = tool, points = pts.toList())
                                                }
                                            } else {
                                                if (lastPan != null) {
                                                    offsetX += ch.position.x - lastPan.x
                                                    offsetY += ch.position.y - lastPan.y
                                                }
                                                lastPan = ch.position
                                            }
                                        }
                                        changes.forEach { c -> c.consume() }
                                    }
                                } while (event.changes.any { c -> c.pressed })

                                if (!pinching && showAnnotations) {
                                    if (tool == "eraser") { pushUndo() }
                                    else {
                                        val cp = curPath
                                        if (cp != null && cp.points.size >= 2) { pushUndo(); commitShape(cp.tool, cp.points); curPath = null }
                                        else curPath = null
                                    }
                                }
                            }
                        }
                ) {
                    cs = IntSize(size.width.toInt(), size.height.toInt())
                    val w = size.width.toInt(); val h = size.height.toInt(); bmp(w, h)
                    val sw2 = src.width.toFloat(); val sh2 = src.height.toFloat()
                    val sc = max(sw2 / w, sh2 / h); val dw = (sw2 / sc).toInt(); val dh = (sh2 / sc).toInt(); val ox = (w - dw) / 2f; val oy = (h - dh) / 2f
                    drawContext.canvas.nativeCanvas.drawBitmap(src, android.graphics.Rect(0, 0, src.width, src.height), android.graphics.Rect(ox.toInt(), oy.toInt(), ox.toInt() + dw, oy.toInt() + dh), null)
                    overlay?.let { drawContext.canvas.nativeCanvas.drawBitmap(it, 0f, 0f, null) }

                    curPath?.let { cur ->
                        val cp = cur.points; if (cp.size >= 2) {
                            val drawColor = Color(selectedColor)
                            when (cur.tool) {
                                "arrow" -> { val sx = cp[0].first;val sy = cp[0].second;val ex = cp[cp.size - 1].first;val ey = cp[cp.size - 1].second;drawLine(drawColor, Offset(sx, sy), Offset(ex, ey), sw);val dx = ex - sx;val dy = ey - sy;if (sqrt(dx * dx + dy * dy) > 1f) { val al = (sw * 6f).coerceAtLeast(20f);val ag = atan2(dy.toDouble(), dx.toDouble());val aa = Math.toRadians(25.0);val ap = androidx.compose.ui.graphics.Path().apply { moveTo(ex, ey);lineTo(ex - al * cos(ag - aa).toFloat(), ey - al * sin(ag - aa).toFloat());moveTo(ex, ey);lineTo(ex - al * cos(ag + aa).toFloat(), ey - al * sin(ag + aa).toFloat()) };drawPath(ap, drawColor, style = Stroke(sw * 1.5f)) } }
                                "circle" -> { val sx = cp[0].first;val sy = cp[0].second;val ex = cp[cp.size - 1].first;val ey = cp[cp.size - 1].second;drawOval(drawColor, Offset((sx + ex) / 2f - abs(ex - sx) / 2f, (sy + ey) / 2f - abs(ey - sy) / 2f), Size(abs(ex - sx), abs(ey - sy)), style = Stroke(sw)) }
                                "rect" -> { val sx = cp[0].first;val sy = cp[0].second;val ex = cp[cp.size - 1].first;val ey = cp[cp.size - 1].second;drawRect(drawColor, Offset(if (sx < ex) sx else ex, if (sy < ey) sy else ey), Size(abs(ex - sx), abs(ey - sy)), style = Stroke(sw)) }
                                "pen" -> { val sp = androidx.compose.ui.graphics.Path();sp.moveTo(cp[0].first, cp[0].second);for (i in 1 until cp.size) sp.lineTo(cp[i].first, cp[i].second);drawPath(sp, drawColor, style = Stroke(sw)) }
                            }
                        }
                    }
                    if (tool == "eraser") { drawCircle(Color.White.copy(0.3f), sw * 3f, epos); drawCircle(Color.White.copy(0.5f), sw * 3f, epos, style = Stroke(2f)) }
                    // Status label
                    val statusText = if (!showAnnotations) "스케치 꺼짐" else when (tool) { "arrow" -> "화살표"; "circle" -> "원"; "rect" -> "사각"; "pen" -> "펜"; else -> "지우개" }
                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(200, 255, 255, 255); textSize = (h * 0.035f).coerceIn(14f, 24f); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                    val sbg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(120, 0, 0, 0); style = Paint.Style.FILL }
                    val tw = sp.measureText(statusText)
                    val pad = 12f
                    drawContext.canvas.nativeCanvas.drawRoundRect(8f, h - sp.textSize - pad * 2, 8f + tw + pad * 2, h - 4f, 6f, 6f, sbg)
                    drawContext.canvas.nativeCanvas.drawText(statusText, 8f + pad, h - pad - 4f, sp)
                }
            }
        }

        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text("도움말", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• 확대: 두 손가락으로 핀치")
                        Text("• 이동: 한 손가락으로 드래그")
                        Text("• 그리기: 스케치 ON 후 한 손가락 드래그")
                        Text("• 지우개: 스케치 ON 후 지우개 선택")
                        Text("• 실행 취소: 되돌리기 버튼")
                        Text("• 저장: 체크 버튼")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelp = false }) { Text("확인") }
                }
            )
        }
    }
}
