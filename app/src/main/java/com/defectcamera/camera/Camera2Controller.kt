package com.defectcamera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

enum class FlashMode { OFF, ON, ALWAYS }

class Camera2Controller(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null

    private var cameraHandlerThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    var currentZoom by mutableFloatStateOf(1f)
    var currentEv by mutableFloatStateOf(0f)
    var currentIso by mutableIntStateOf(0)
    var currentShutterSpeed by mutableStateOf("Auto")
    var flashMode by mutableStateOf(FlashMode.OFF)
    var isManualFocus by mutableStateOf(false)

    private var manualIso = 0
    private var manualSsNs = 0L
    private var manualFocusDistance = 0.2f
    private var previewBuilder: CaptureRequest.Builder? = null
    private var captureCallback: ((File) -> Unit)? = null
    private var cameraId = ""
    private var sensorOrientation = 0

    private val stateLock = ReentrantLock()
    private var isTakingPhoto = false

    fun startCamera(surfaceTexture: SurfaceTexture, onReady: () -> Unit) {
        startBackgroundThreads()
        surfaceTexture.setDefaultBufferSize(1920, 1080)
        previewSurface = Surface(surfaceTexture)

        try {
            cameraId = cameraManager.cameraIdList.first { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession(onReady)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createPreviewSession(onReady: () -> Unit) {
        val device = cameraDevice ?: return
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val sizes = map.getOutputSizes(ImageFormat.JPEG)
        val maxSize = sizes.maxByOrNull { it.width * it.height } ?: Size(4032, 3024)

        imageReader = ImageReader.newInstance(maxSize.width, maxSize.height, ImageFormat.JPEG, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            backgroundHandler?.post { processImage(reader) }
        }, backgroundHandler!!)

        try {
            val surfaces = listOf(previewSurface!!, imageReader!!.surface)

            val pBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            pBuilder.addTarget(previewSurface!!)
            pBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            previewBuilder = pBuilder

            val outputs = surfaces.map { OutputConfiguration(it) }
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                { cameraHandler!!.post(it) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        pBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        session.setRepeatingRequest(pBuilder.build(), null, cameraHandler)
                        onReady()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }
            )
            device.createCaptureSession(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setZoom(ratio: Float) {
        currentZoom = ratio
        rebuildPreview()
    }

    fun setEv(ev: Float) {
        currentEv = ev
        rebuildPreview()
    }

    fun setIso(iso: Int) {
        currentIso = iso
        manualIso = iso
        applyManualParams()
    }

    fun setShutterSpeed(speedNs: Long) {
        manualSsNs = speedNs
        currentShutterSpeed = if (speedNs > 0) {
            "1/${1_000_000_000L / speedNs}"
        } else {
            "Auto"
        }
        applyManualParams()
    }

    private fun applyManualParams() {
        rebuildPreview()
    }

    private fun rebuildPreview() {
        val device = cameraDevice ?: return
        try {
            val pb = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            pb.addTarget(previewSurface!!)
            pb.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            if (isManualFocus) {
                pb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                pb.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
            } else {
                pb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            if (manualIso > 0 || manualSsNs > 0) {
                pb.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                if (manualIso > 0) pb.set(CaptureRequest.SENSOR_SENSITIVITY, manualIso)
                if (manualSsNs > 0) pb.set(CaptureRequest.SENSOR_EXPOSURE_TIME, manualSsNs)
            } else {
                pb.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            val ev = currentEv
            pb.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (ev * 6).toInt())

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            val ratio = currentZoom.coerceIn(1f, maxZoom)
            val rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (rect != null) {
                val cropW = (rect.width() / ratio).toInt()
                val cropH = (rect.height() / ratio).toInt()
                val cropX = ((rect.width() - cropW) / 2).toInt()
                val cropY = ((rect.height() - cropH) / 2).toInt()
                pb.set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH))
            }

            when (flashMode) {
                FlashMode.OFF -> pb.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                FlashMode.ON -> pb.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                FlashMode.ALWAYS -> pb.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }

            previewBuilder = pb
            captureSession?.setRepeatingRequest(pb.build(), null, cameraHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleManualFocus() {
        isManualFocus = !isManualFocus
        rebuildPreview()
    }

    fun setManualFocus(distance: Float) {
        if (isManualFocus) {
            manualFocusDistance = distance
            rebuildPreview()
        }
    }

    fun toggleFlash() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.ALWAYS
            FlashMode.ALWAYS -> FlashMode.OFF
        }
        rebuildPreview()
    }

    fun takePhoto(
        defectFolder: String = "Other",
        onPhotoSaved: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isTakingPhoto) return
        isTakingPhoto = true
        captureCallback = onPhotoSaved

        try {
            val device = cameraDevice ?: throw Exception("Camera not open")
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: throw Exception("No config")
            val sizes = map.getOutputSizes(ImageFormat.JPEG)
            val maxSize = sizes.maxByOrNull { it.width * it.height } ?: Size(4032, 3024)

            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)

            if (manualIso > 0 || manualSsNs > 0) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                if (manualIso > 0) captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, manualIso)
                if (manualSsNs > 0) captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, manualSsNs)
            } else {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            val ev = currentEv
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (ev * 6).toInt())

            when (flashMode) {
                FlashMode.ON -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                FlashMode.ALWAYS -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                FlashMode.OFF -> captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())

            val session = captureSession ?: throw Exception("No session")
            session.stopRepeating()
            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                }
            }, cameraHandler)

        } catch (e: Exception) {
            isTakingPhoto = false
            onError(e.message ?: "Capture failed")
        }
    }

    private fun processImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA).format(Date())
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val folder = "DefectCamera/${dateStr}"
        val dir = File(context.getExternalFilesDir(null), "Pictures/$folder")
        dir.mkdirs()
        val file = File(dir, "IMG_$dateStamp.jpg")

        try {
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        image.close()

        stateLock.withLock {
            isTakingPhoto = false
        }

        captureCallback?.invoke(file)
        captureCallback = null

        cameraHandler?.post {
            try {
                captureSession?.setRepeatingRequest(previewBuilder?.build() ?: return@post, null, cameraHandler)
            } catch (_: Exception) {}
        }
    }

    private fun startBackgroundThreads() {
        cameraHandlerThread = HandlerThread("CameraThread").also { it.start() }
        cameraHandler = Handler(cameraHandlerThread!!.looper)
        backgroundThread = HandlerThread("BackgroundThread").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun release() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraHandlerThread?.quitSafely()
        backgroundThread?.quitSafely()
    }
}
