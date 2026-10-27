package com.codefield.snapledger.system

import android.graphics.Bitmap
import com.codefield.snapledger.model.OcrBox
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 用 ML Kit 在手机上识别图片里的字，不联网。
 * 韩文模型和中文模型都认识英文和数字，但不认识对方的字，所以两个都读一遍，由 logic 里的 ReadingChooser 挑一份。
 */
class TextScanner {

    private val recognizers by lazy {
        listOf(
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
        )
    }

    /** 每个模型读出的一段段字（先韩文，后中文）。 */
    suspend fun read(bitmap: Bitmap): List<List<OcrBox>> = coroutineScope {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizers.map { recognizer -> async { recognizer.process(image).await().boxes() } }.awaitAll()
    }

    private fun Text.boxes(): List<OcrBox> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
        line.boundingBox?.let { OcrBox(line.text, it.left, it.top, it.right, it.bottom) }
    }
}

/** 等 Google 的 Task 做完，好在协程里按顺序写。 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
