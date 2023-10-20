package com.example.zxingcppdemo

import android.graphics.Rect
import androidx.test.runner.AndroidJUnit4
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZxingCppTest {
	@Test
	fun encodeDecode() {
		encodeDecodeString(
			"The quick brown fox jumps over the lazy dog.",
			Format.QR_CODE
		)
		encodeDecodeString(
			"This is test 😁",
			Format.DATA_MATRIX
		)
	}

	@Test
	fun encodeDecodeBinary() {
		val size = 256
		val bytes = ByteArray(size)
		for (i in 0 until size) {
			bytes[i] = (i % 256).toByte()
		}
		encodeDecodeByteArray(bytes, Format.QR_CODE)
		encodeDecodeByteArray(bytes, Format.AZTEC)
		encodeDecodeByteArray(bytes, Format.DATA_MATRIX)
	}
}

fun encodeDecodeString(text: String, format: Format) {
	val bitmap = ZxingCpp.encodeAsBitmap(text, format)
	assertNotNull(bitmap)
	val results = bitmap.run {
		ZxingCpp.readBitmap(
			this,
			Rect(0, 0, width, height)
		)
	}
	assertEquals(1, results?.size)
	val result = results?.first()
	assertNotNull(result)
	assertEquals(text, result?.text)
}

fun encodeDecodeByteArray(bytes: ByteArray, format: Format) {
	val bitmap = ZxingCpp.encodeAsBitmap(bytes, format)
	assertNotNull(bitmap)
	val results = bitmap.run {
		ZxingCpp.readBitmap(
			this,
			Rect(0, 0, width, height)
		)
	}
	assertEquals(1, results?.size)
	val result = results?.first()
	assertNotNull(result)
	assertEquals(ZxingCpp.ContentType.BINARY, result?.contentType)
	val decodedBytes = result?.rawBytes ?: throw IllegalStateException()
	assertNotNull(decodedBytes)
	assertEquals(bytes.size, decodedBytes.size)
	val size = bytes.size
	for (i in 0 until size) {
		assertEquals(bytes[i], decodedBytes[i])
	}
}
