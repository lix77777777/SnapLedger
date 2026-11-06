package com.codefield.snapledger.system

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把文字写进用户在系统「另存为」里选的文件（导出 CSV）。地址来自系统的文件选择界面，
 * 通过 ContentResolver 写，不用申请存储权限；存到手机、下载文件夹还是云盘由用户决定。
 */
class DocumentWriter(private val context: Context) {

    /** 按 UTF-8 写入，覆盖原来的内容。写不进去时抛 IOException。 */
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        open(uri).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** 先用「wt」（写入并清空旧内容）；有的云盘不支持这个模式，再退回「w」。 */
    private fun open(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (e: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw IOException("Cannot open $uri")
    }
}
