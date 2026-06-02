package com.defectcamera.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.defectcamera.camera.Camera2Controller
import com.defectcamera.camera.FlashMode
import java.io.File

enum class Screen { CAMERA, ANNOTATION }

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.CAMERA) }
    var lastPhotoFile by remember { mutableStateOf<File?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    val cameraController = remember { Camera2Controller(context) }

    var isoValue by remember { mutableIntStateOf(0) }
    var ssNs by remember { mutableStateOf(0L) }
    var ev by remember { mutableFloatStateOf(-0.7f) }
    var isManualFocus by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }

    fun saveToGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DefectCamera")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().use { inp -> inp.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            }
        } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                            cameraController.startCamera(surface) { cameraReady = true }
                        }
                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { cameraController.toggleFlash() }) {
                    Icon(
                        if (cameraController.flashMode == FlashMode.OFF) Icons.Outlined.FlashOff else Icons.Default.FlashOn,
                        "Flash",
                        tint = if (cameraController.flashMode == FlashMode.OFF) Color.Gray else Color.Yellow
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                    Icon(Icons.Default.Search, "Z", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Slider(value = zoom, onValueChange = { cameraController.setZoom(it); zoom = it }, valueRange = 1f..10f, steps = 89, modifier = Modifier.weight(1f).height(20.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF3D00), activeTrackColor = Color(0xFFFF3D00), inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(4.dp))
                    Text("%.1fX".format(zoom), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Column(Modifier.fillMaxSize().padding(bottom = 12.dp), verticalArrangement = Arrangement.Bottom) {
            CameraControlBar(
                ev = ev, onEvChange = { cameraController.setEv(it); ev = it },
                isManualFocus = isManualFocus,
                onFocusToggle = { cameraController.toggleManualFocus(); isManualFocus = !isManualFocus },
                onManualFocusChange = { cameraController.setManualFocus(it) },
                isoValue = isoValue, onIsoChange = { cameraController.setIso(it); isoValue = it },
                ssNs = ssNs, onSsChange = { cameraController.setShutterSpeed(it); ssNs = it },
                onShutterClick = {
                    cameraController.takePhoto(
                        defectFolder = "Defect",
                        onPhotoSaved = { file ->
                            saveToGallery(file)
                            lastPhotoFile = file
                            currentScreen = Screen.ANNOTATION
                        },
                        onError = { }
                    )
                },
                onGalleryClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_GALLERY)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {}
                }
            )
        }

        if (currentScreen == Screen.ANNOTATION) {
            AnnotationScreen(
                photoFile = lastPhotoFile,
                onBack = { currentScreen = Screen.CAMERA },
                onSaved = { currentScreen = Screen.CAMERA }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraController.release() }
    }
}
