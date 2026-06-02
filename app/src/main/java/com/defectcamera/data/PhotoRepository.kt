package com.defectcamera.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.defectcamera.data.models.DefectType
import com.defectcamera.data.models.Photo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRepository(private val context: Context) {

    fun saveAnnotatedPhoto(
        bitmap: Bitmap,
        originalFile: File,
        defectType: DefectType? = null,
        onSaved: (Photo) -> Unit
    ) {
        val defectFolder = defectType?.folder ?: "Other"
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        val folderName = "DefectCamera/${defectFolder}_$dateStr"
        val fileName = "ANNOTATED_${originalFile.name}"

        val dir = File(context.getExternalFilesDir(null), "Pictures/$folderName")
        dir.mkdirs()
        val file = File(dir, fileName)

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }

        onSaved(
            Photo(
                uri = Uri.fromFile(file),
                file = file,
                defectType = defectType
            )
        )
    }

    fun getLatestPhoto(): File? {
        val base = File(context.getExternalFilesDir(null), "Pictures/DefectCamera")
        if (!base.exists()) return null
        return base.listFiles()
            ?.flatMap { it.listFiles()?.toList() ?: emptyList() }
            ?.maxByOrNull { it.lastModified() }
    }
}
