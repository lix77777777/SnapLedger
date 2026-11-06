package com.codefield.snapledger.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把图片（回顾的总结卡）存成 PNG，通过 FileProvider 交给别的应用分享。文件放在缓存里，系统需要空间时会清掉。 */
class ImageSharer(private val context: Context) {

    /** 存好图片，返回系统的分享面板。name 只用字母、数字和横线。 */
    suspend fun shareIntent(bitmap: Bitmap, name: String, chooserTitle: String): Intent = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val file = File(folder, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // 有些应用只看 ClipData 里的地址，读取权限也跟着它走。
        send.clipData = ClipData.newRawUri(null, uri)
        Intent.createChooser(send, chooserTitle)
    }

    private companion object {
        /** 和 res/xml/file_paths.xml 里的 cache-path 一致。 */
        const val FOLDER = "shared"
        /** PNG 是无损格式，这个数字会被忽略，写 100 只是说明意图。 */
        const val PNG_QUALITY = 100
    }
}
