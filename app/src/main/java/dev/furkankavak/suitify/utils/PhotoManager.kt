package dev.furkankavak.suitify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.scale

class PhotoManager(private val context: Context) {
    
    companion object {
        const val MAX_IMAGE_SIZE = 1024 * 1024 // 1MB
        const val JPEG_QUALITY = 85
        const val MAX_DIMENSION = 1024
    }

    private val tempFiles = mutableListOf<File>()

    // Sealed class for photo operation results
    sealed class PhotoResult {
        data class Success(val data: ByteArray) : PhotoResult()
        data class Error(val message: String) : PhotoResult()
    }

    /**
     * Kamera için geçici dosya oluşturur
     */
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(context.cacheDir, "images")
        
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        tempFiles.add(imageFile)
        return imageFile
    }

    /**
     * Dosya için FileProvider URI'si oluşturur
     */
    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * URI'den bitmap oluşturur ve boyutunu optimize eder
     */
    fun optimizeForApiSafe(uri: Uri): PhotoResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val originalBitmap = BitmapFactory.decodeStream(stream)
                
                if (originalBitmap == null) {
                    return PhotoResult.Error("Fotoğraf yüklenemedi")
                }

                // Orientation'ı düzelt
                val rotatedBitmap = fixImageOrientation(uri, originalBitmap)
                
                // Boyutu optimize et
                val optimizedBitmap = optimizeBitmap(rotatedBitmap)
                
                // JPEG'e çevir
                val outputStream = ByteArrayOutputStream()
                optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                val byteArray = outputStream.toByteArray()
                
                // Memory'yi temizle
                if (originalBitmap != rotatedBitmap) {
                    originalBitmap.recycle()
                }
                rotatedBitmap.recycle()
                optimizedBitmap.recycle()
                
                PhotoResult.Success(byteArray)
            } ?: PhotoResult.Error("Dosya açılamadı")
        } catch (e: Exception) {
            PhotoResult.Error("Fotoğraf işlenirken hata oluştu: ${e.message}")
        }
    }

    /**
     * Bitmap'in boyutunu optimize eder
     */
    private fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Eğer resim zaten küçükse, olduğu gibi döndür
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return bitmap
        }
        
        // Ölçekleme faktörünü hesapla
        val scaleFactor = if (width > height) {
            MAX_DIMENSION.toFloat() / width
        } else {
            MAX_DIMENSION.toFloat() / height
        }
        
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * EXIF verilerine göre resmin yönünü düzeltir
     */
    private fun fixImageOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: IOException) {
            // EXIF okunamazsa, orijinal bitmap'i döndür
            bitmap
        }
    }

    /**
     * Bitmap'i belirtilen açıyla döndürür
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Byte array'in KB cinsinden boyutunu hesaplar
     */
    fun getImageSizeInKB(data: ByteArray): Double {
        return data.size / 1024.0
    }

    /**
     * Byte array'in MB cinsinden boyutunu hesaplar
     */
    fun getImageSizeInMB(data: ByteArray): Double {
        return data.size / (1024.0 * 1024.0)
    }

    /**
     * Geçici dosyaları temizler
     */
    fun cleanupTempFiles() {
        tempFiles.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Silme hatası olursa görmezden gel
            }
        }
        tempFiles.clear()
    }

    /**
     * Önbellek dizinini temizler
     */
    fun clearImageCache() {
        try {
            val cacheDir = File(context.cacheDir, "images")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            // Temizlik hatası olursa görmezden gel
        }
    }
}

