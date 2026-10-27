package com.codefield.snapledger.logic

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNameTimeTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val expected = LocalDateTime.of(2026, 10, 28, 14, 32, 5).atZone(seoul).toInstant().toEpochMilli()

    @Test
    fun commonScreenshotNames() {
        assertEquals(expected, FileNameTime.parse("Screenshot_20261028_143205_KakaoPay.jpg", seoul))
        assertEquals(expected, FileNameTime.parse("Screenshot_20261028-143205.png", seoul))
        assertEquals(expected, FileNameTime.parse("Screenshot_2026-10-28-14-32-05-123_com.tencent.mm.jpg", seoul))
    }

    @Test
    fun namesWithoutAValidTime() {
        assertNull(FileNameTime.parse("receipt.png", seoul))
        assertNull(FileNameTime.parse("Screenshot_20261341_256199.png", seoul))
        assertNull(FileNameTime.parse("photo_1202610281432051.jpg", seoul))
    }
}
