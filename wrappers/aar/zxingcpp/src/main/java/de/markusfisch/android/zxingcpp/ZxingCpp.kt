/*
* Copyright 2021 Axel Waggershauser
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package de.markusfisch.android.zxingcpp

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import java.nio.ByteBuffer

object ZxingCpp {
	// Enumerates barcode formats known to this package.
	// Note that this has to be kept synchronized with native (C++/JNI) side.
	enum class Format {
		NONE,
		AZTEC,
		CODABAR,
		CODE_39,
		CODE_93,
		CODE_128,
		DATA_BAR,
		DATA_BAR_EXPANDED,
		DATA_MATRIX,
		EAN_8,
		EAN_13,
		ITF,
		MAXICODE,
		PDF_417,
		QR_CODE,
		MICRO_QR_CODE,
		UPC_A,
		UPC_E,
	}
	enum class ContentType {
		TEXT, BINARY, MIXED, GS1, ISO15434, UNKNOWN_ECI
	}

	data class Position(
		val topLeft: Point,
		val topRight: Point,
		val bottomLeft: Point,
		val bottomRight: Point,
		val orientation: Double
	)

	data class GTIN(
		val country: String,
		val addOn: String,
		val price: String,
		val issueNumber: String
	)

	data class Result(
		val format: String,
		val contentType: ContentType,
		val text: String,
		val position: Position,
		val orientation: Int,
		val rawBytes: ByteArray,
		val ecLevel: String,
		val symbologyIdentifier: String,
		val sequenceSize: Int,
		val sequenceIndex: Int,
		val sequenceId: String,
		val readerInit: Boolean,
		val lineCount: Int,
		val versionNumber: Int,
		val gtin: GTIN?
	)

	fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		formats: Set<Format> = setOf(),
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result? = readYBuffer(
		yBuffer,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		formats.joinToString(),
		tryHarder,
		tryRotate,
		tryInvert,
		tryDownscale
	)

	external fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int = 0,
		formats: String = "",
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result?

	fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		formats: Set<Format> = setOf(),
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result? = readByteArray(
		yuvData,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		formats.joinToString(),
		tryHarder,
		tryRotate,
		tryInvert,
		tryDownscale
	)

	external fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int = 0,
		formats: String = "",
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result?

	fun readBitmap(
		bitmap: Bitmap,
		cropRect: Rect,
		rotation: Int = 0,
		formats: Set<Format> = setOf(),
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result? = readBitmap(
		bitmap,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		formats.joinToString(),
		tryHarder,
		tryRotate,
		tryInvert,
		tryDownscale
	)

	external fun readBitmap(
		bitmap: Bitmap,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int = 0,
		formats: String = "",
		tryHarder: Boolean = false,
		tryRotate: Boolean = false,
		tryInvert: Boolean = false,
		tryDownscale: Boolean = false
	): Result?

	data class BitMatrix(
		val width: Int,
		val height: Int,
		val data: ByteArray
	) {
		fun get(x: Int, y: Int) = data[y * width + x] == 0.toByte()
	}

	fun encodeAsBitmap(
		text: String,
		format: Format,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1,
		encoding: String = "UTF-8",
		setColor: Int = 0xff000000.toInt(),
		unsetColor: Int = 0xffffffff.toInt()
	): Bitmap {
		val bitMatrix = encode(
			text, format.toString(),
			width, height,
			margin, ecLevel, encoding
		)
		val w = bitMatrix.width
		val h = bitMatrix.height
		val pixels = IntArray(w * h)
		var offset = 0
		for (y in 0 until h) {
			for (x in 0 until w) {
				pixels[offset + x] = if (bitMatrix.get(x, y)) {
					setColor
				} else {
					unsetColor
				}
			}
			offset += w
		}
		val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
		return bitmap
	}

	fun encodeAsSvg(
		text: String,
		format: Format,
		margin: Int = -1,
		ecLevel: Int = -1,
		encoding: String = "UTF-8"
	): String {
		val bitMatrix = encode(
			text, format.toString(),
			0, 0,
			margin, ecLevel, encoding
		)
		val sb = StringBuilder()
		val w = bitMatrix.width
		var h = bitMatrix.height
		val moduleHeight = if (h == 1) w / 2 else 1
		for (y in 0 until h) {
			for (x in 0 until w) {
				if (bitMatrix.get(x, y)) {
					sb.append(" M${x},${y}h1v${moduleHeight}h-1z")
				}
			}
		}
		h *= moduleHeight
		return """<svg width="$w" height="$h"
viewBox="0 0 $w $h"
xmlns="http://www.w3.org/2000/svg">
<path d="$sb"/>
</svg>
"""
	}

	fun encodeAsText(
		text: String,
		format: Format,
		margin: Int = -1,
		ecLevel: Int = -1,
		encoding: String = "UTF-8",
		inverted: Boolean = false
	): String {
		val bitMatrix = encode(
			text, format.toString(),
			0, 0,
			margin, ecLevel, encoding
		)
		val w = bitMatrix.width
		val h = bitMatrix.height
		val sb = StringBuilder()
		val map = if (inverted) "█▄▀ " else " ▀▄█"
		fun Boolean.toInt() = if (this) 1 else 0
		for (y in 0 until h step 2) {
			for (x in 0 until w) {
				val tp = bitMatrix.get(y, x).toInt()
				val bt = (y + 1 < h && bitMatrix.get(y + 1, x)).toInt()
				sb.append(map[tp or (bt shl 1)])
			}
			sb.append('\n')
		}
		return sb.toString()
	}

	external fun encode(
		text: String,
		format: String,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1,
		encoding: String = "UTF-8"
	): BitMatrix

	init {
		System.loadLibrary("zxing")
	}
}
