package com.defectcamera.data.models

import android.net.Uri
import java.io.File

data class Photo(
    val uri: Uri,
    val file: File,
    val timestamp: Long = System.currentTimeMillis(),
    val defectType: DefectType? = null,
    val magnification: Float = 1f,
    val iso: Int = 0,
    val shutterSpeed: String = "",
    val hasAnnotations: Boolean = false
)
