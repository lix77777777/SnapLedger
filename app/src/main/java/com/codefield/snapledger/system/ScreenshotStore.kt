package com.codefield.snapledger.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.codefield.snapledger.logic.FileNameTime
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 截图的副本，存在应用自己的文件夹（files/screenshots）里，账目里只记文件名。
 * 别的应用给的读取权限不长久，所以收到截图就马上存一份，之后识别、显示都用这一份。
 */
class ScreenshotStore(private val context: Context) {

    /** 存好的一张截图：文件名，以及从文件名或相册信息看出的截图时间（看不出来是 null）。 */
    data class Saved(val name: String, val takenAt: Long?)

    private val folder = File(context.filesDir, "screenshots")

    fun file(name: String): File = File(folder, name)

    /** 存一份。图片打不开时返回 null。 */
    suspend fun save(uri: Uri): Saved? = withContext(Dispatchers.IO) {
        val bitmap = runCatching { decode(uri) }.getOrNull() ?: return@withContext null
        try {
            folder.mkdirs()
            val name = "${UUID.randomUUID()}.jpg"
            FileOutputStream(file(name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            Saved(name, takenAt(uri))
        } catch (e: IOException) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun load(name: String): Bitmap? = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file(name).path) }

    suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { file(name).delete() }
    }

    /**
     * 删掉没人用的截图（账目删除后没有撤销的）。只删一天以前存的：刚存的可能正在另一个窗口里等着确认
     * （从别的应用分享时会另开一个窗口）。
     */
    suspend fun removeUnused(used: Set<String>) {
        withContext(Dispatchers.IO) {
            val before = System.currentTimeMillis() - KEEP_MILLIS
            folder.listFiles()?.filter { it.name !in used && it.lastModified() < before }?.forEach { it.delete() }
        }
    }

    /**
     * 读图片。比 1440 像素宽的缩小到 1440 宽，字还看得清，文件也不会太大；长截图不限高度。
     * 拍纸质小票的照片可能是横着存的，要按照片信息（EXIF）转正，不然识别不了。
     */
    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        // 先只读尺寸（这时 decodeStream 本来就返回 null），读不出尺寸就不是能打开的图片。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH ||
            bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_PIXELS
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        if (bitmap.width > MAX_WIDTH) {
            bitmap = bitmap.replaceWith(bitmap.scale(MAX_WIDTH, bitmap.height * MAX_WIDTH / bitmap.width))
        }
        val degrees = runCatching { resolver.openInputStream(uri)?.use { rotationOf(ExifInterface(it)) } }.getOrNull() ?: 0
        if (degrees != 0) {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            bitmap = bitmap.replaceWith(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
        }
        return bitmap
    }

    private fun Bitmap.replaceWith(next: Bitmap): Bitmap {
        if (next !== this) recycle()
        return next
    }

    private fun rotationOf(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /** 截图时间：先看文件名（「Screenshot_20261028_143205.jpg」），再看相册记的拍摄时间。 */
    private fun takenAt(uri: Uri): Long? {
        var name: String? = null
        var taken: Long? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn)
                    val takenColumn = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
                    if (takenColumn >= 0 && !cursor.isNull(takenColumn)) taken = cursor.getLong(takenColumn)
                }
            }
        }
        return name?.let { FileNameTime.parse(it, ZoneId.systemDefault()) } ?: taken?.takeIf { it > 0 }
    }

    private companion object {
        const val MAX_WIDTH = 1440
        const val MAX_PIXELS = 25_000_000L
        const val JPEG_QUALITY = 90
        const val KEEP_MILLIS = 24 * 60 * 60 * 1000L
    }
}
