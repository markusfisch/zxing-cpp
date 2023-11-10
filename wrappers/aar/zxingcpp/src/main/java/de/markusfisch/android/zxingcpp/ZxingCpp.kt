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
	// These enums have to be kept in sync with the native (C++/JNI) side.
	enum class ContentType {
		TEXT, BINARY, MIXED, GS1, ISO15434, UNKNOWN_ECI
	}

	enum class Binarizer {
		LOCAL_AVERAGE, GLOBAL_HISTOGRAM, FIXED_THRESHOLD, BOOL_CAST
	}

	enum class EanAddOnSymbol {
		IGNORE, READ, REQUIRE
	}

	enum class TextMode {
		PLAIN, ECI, HRI, HEX, ESCAPED
	}

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

	data class DecodeHints(
		var formats: String = "",
		var tryHarder: Boolean = true,
		var tryRotate: Boolean = true,
		var tryInvert: Boolean = true,
		var tryDownscale: Boolean = true,
		var isPure: Boolean = false,
		var tryCode39ExtendedMode: Boolean = false,
		var validateCode39CheckSum: Boolean = false,
		var validateITFCheckSum: Boolean = false,
		var returnCodabarStartEnd: Boolean = false,
		var returnErrors: Boolean = false,
		var downscaleFactor: Int = 3,
		var eanAddOnSymbol: EanAddOnSymbol = EanAddOnSymbol.IGNORE,
		var binarizer: Binarizer = Binarizer.LOCAL_AVERAGE,
		var textMode: TextMode = TextMode.HRI,
		var minLineCount: Int = 2,
		var maxNumberOfSymbols: Int = 0xff,
		var downscaleThreshold: Int = 500
	)

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
		val version: String,
		val gtin: GTIN?
	)

	fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		decodeHints: DecodeHints = DecodeHints(),
	): List<Result>? = readYBuffer(
		yBuffer,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		decodeHints,
	)

	external fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		decodeHints: DecodeHints,
	): List<Result>?

	fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		decodeHints: DecodeHints = DecodeHints(),
	): List<Result>? = readByteArray(
		yuvData,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		decodeHints,
	)

	external fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		decodeHints: DecodeHints,
	): List<Result>?

	fun readBitmap(
		bitmap: Bitmap,
		cropRect: Rect,
		rotation: Int = 0,
		decodeHints: DecodeHints = DecodeHints(),
	): List<Result>? = readBitmap(
		bitmap,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		decodeHints,
	)

	external fun readBitmap(
		bitmap: Bitmap,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		decodeHints: DecodeHints,
	): List<Result>?

	data class BitMatrix(
		val width: Int,
		val height: Int,
		val data: ByteArray
	) {
		fun get(x: Int, y: Int) = data[y * width + x] == 0.toByte()
	}

	fun BitMatrix.toBitmap(
		setColor: Int = 0xff000000.toInt(),
		unsetColor: Int = 0xffffffff.toInt()
	): Bitmap {
		val pixels = IntArray(width * height)
		var offset = 0
		for (y in 0 until height) {
			for (x in 0 until width) {
				pixels[offset + x] = if (get(x, y)) {
					setColor
				} else {
					unsetColor
				}
			}
			offset += width
		}
		val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
		return bitmap
	}

	fun BitMatrix.toSvg(): String {
		val sb = StringBuilder()
		val moduleHeight = if (height == 1) width / 2 else 1
		for (y in 0 until height) {
			for (x in 0 until width) {
				if (get(x, y)) {
					sb.append(" M${x},${y}h1v${moduleHeight}h-1z")
				}
			}
		}
		val h = height * moduleHeight
		return """<svg width="$width" height="$h"
viewBox="0 0 $width $h"
xmlns="http://www.w3.org/2000/svg">
<path d="$sb"/>
</svg>
"""
	}

	fun BitMatrix.toText(
		inverted: Boolean = false
	): String {
		val sb = StringBuilder()
		fun Boolean.toInt() = if (this) 1 else 0
		if (height == 1) {
			val map = if (inverted) "█ " else " █"
			for (y in 0 until height) {
				for (x in 0 until width) {
					sb.append(map[get(x, y).toInt()])
				}
				sb.append('\n')
			}
		} else {
			val map = if (inverted) "█▄▀ " else " ▀▄█"
			for (y in 0 until height step 2) {
				for (x in 0 until width) {
					val tp = get(x, y).toInt()
					val bt = (y + 1 < height && get(x, y + 1)).toInt()
					sb.append(map[tp or (bt shl 1)])
				}
				sb.append('\n')
			}
		}
		return sb.toString()
	}

	fun <T> encodeAsBitmap(
		content: T,
		format: Format,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1,
		setColor: Int = 0xff000000.toInt(),
		unsetColor: Int = 0xffffffff.toInt()
	): Bitmap = content.encode(
		format.toString(),
		width, height,
		margin, ecLevel
	).toBitmap(setColor, unsetColor)

	fun <T> encodeAsSvg(
		content: T,
		format: Format,
		margin: Int = -1,
		ecLevel: Int = -1
	): String = content.encode(
		format.toString(),
		0, 0,
		margin, ecLevel
	).toSvg()

	fun <T> encodeAsText(
		content: T,
		format: Format,
		margin: Int = -1,
		ecLevel: Int = -1,
		inverted: Boolean = false
	): String = content.encode(
		format.toString(),
		0, 0,
		margin, ecLevel
	).toText(inverted)

	fun <T> T.encode(
		format: String,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1
	): BitMatrix = when (this) {
		is String -> encodeString(
			this, format, width, height, margin, ecLevel
		)

		is ByteArray -> encodeByteArray(
			this, format, width, height, margin, ecLevel
		)

		else -> throw IllegalArgumentException(
			"encode() is not implemented for this type"
		)
	}

	private external fun encodeString(
		text: String,
		format: String,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1
	): BitMatrix

	private external fun encodeByteArray(
		bytes: ByteArray,
		format: String,
		width: Int = 0,
		height: Int = 0,
		margin: Int = -1,
		ecLevel: Int = -1
	): BitMatrix

	init {
		System.loadLibrary("zxing")
	}
}
