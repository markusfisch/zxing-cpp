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
	enum class Binarizer {
		LOCAL_AVERAGE, GLOBAL_HISTOGRAM, FIXED_THRESHOLD, BOOL_CAST
	}

	enum class ContentType {
		TEXT, BINARY, MIXED, GS1, ISO15434, UNKNOWN_ECI
	}

	enum class EanAddOnSymbol {
		IGNORE, READ, REQUIRE
	}

	enum class ErrorType {
		NONE, FORMAT, CHECKSUM, UNSUPPORTED
	}

	enum class BarcodeFormat(val value: Int) {
		None(0x0000),
		All(0x2A2A),
		AllReadable(0x722A),
		AllCreatable(0x772A),
		AllLinear(0x6C2A),
		AllMatrix(0x6D2A),
		AllGS1(0x472A),
		AllRetail(0x522A),
		AllIndustrial(0x492A),
		Codabar(0x2046),
		Code39(0x2041),
		Code39Std(0x7341),
		Code39Ext(0x6541),
		Code32(0x3241),
		PZN(0x7041),
		Code93(0x2047),
		Code128(0x2043),
		ITF(0x2049),
		ITF14(0x3449),
		DataBar(0x2065),
		DataBarOmni(0x6F65),
		DataBarStk(0x7365),
		DataBarStkOmni(0x4F65),
		DataBarLtd(0x6C65),
		DataBarExp(0x6565),
		DataBarExpStk(0x4565),
		EANUPC(0x2045),
		EAN13(0x3145),
		EAN8(0x3845),
		EAN5(0x3545),
		EAN2(0x3245),
		ISBN(0x6945),
		UPCA(0x6145),
		UPCE(0x6545),
		OtherBarcode(0x2058),
		DXFilmEdge(0x7858),
		PDF417(0x204C),
		CompactPDF417(0x634C),
		MicroPDF417(0x6D4C),
		Aztec(0x207A),
		AztecCode(0x637A),
		AztecRune(0x727A),
		QRCode(0x2051),
		QRCodeModel1(0x3151),
		QRCodeModel2(0x3251),
		MicroQRCode(0x6D51),
		RMQRCode(0x7251),
		DataMatrix(0x2064),
		MaxiCode(0x2055),
	}

	enum class TextMode {
		PLAIN, ECI, HRI, ESCAPED, HEX, HEX_ECI
	}

	data class ReaderOptions(
		var formats: Set<BarcodeFormat> = setOf(),
		var tryHarder: Boolean = true,
		var tryRotate: Boolean = true,
		var tryInvert: Boolean = true,
		var tryDownscale: Boolean = true,
		var isPure: Boolean = false,
		var binarizer: Binarizer = Binarizer.LOCAL_AVERAGE,
		var downscaleFactor: Int = 3,
		var downscaleThreshold: Int = 500,
		var minLineCount: Int = 2,
		var maxNumberOfSymbols: Int = 0xff,
		var tryCode39ExtendedMode: Boolean = false,
		var validateCode39CheckSum: Boolean = false,
		var validateITFCheckSum: Boolean = false,
		var returnErrors: Boolean = false,
		var eanAddOnSymbol: EanAddOnSymbol = EanAddOnSymbol.IGNORE,
		var textMode: TextMode = TextMode.HRI,
	)

	data class Error(
		val type: ErrorType,
		val message: String
	)

	data class GTIN(
		val country: String,
		val addOn: String,
		val price: String,
		val issueNumber: String
	)

	data class Position(
		val topLeft: Point,
		val topRight: Point,
		val bottomLeft: Point,
		val bottomRight: Point,
		val orientation: Double
	)

	data class Result(
		val format: BarcodeFormat,
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
		val dataMask: Int,
		val lineCount: Int,
		val version: String,
		val symbol: BitMatrix?,
		val gtin: GTIN?,
		val error: Error?
	)

	fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		options: ReaderOptions = ReaderOptions(),
	): List<Result>? = readYBuffer(
		yBuffer,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		options,
	)

	external fun readYBuffer(
		yBuffer: ByteBuffer,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		options: ReaderOptions,
	): List<Result>?

	fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		cropRect: Rect,
		rotation: Int = 0,
		options: ReaderOptions = ReaderOptions(),
	): List<Result>? = readByteArray(
		yuvData,
		rowStride,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		options,
	)

	external fun readByteArray(
		yuvData: ByteArray,
		rowStride: Int,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		options: ReaderOptions,
	): List<Result>?

	fun readBitmap(
		bitmap: Bitmap,
		cropRect: Rect,
		rotation: Int = 0,
		options: ReaderOptions = ReaderOptions(),
	): List<Result>? = readBitmap(
		bitmap,
		cropRect.left, cropRect.top,
		cropRect.width(), cropRect.height(),
		rotation,
		options,
	)

	external fun readBitmap(
		bitmap: Bitmap,
		left: Int, top: Int,
		width: Int, height: Int,
		rotation: Int,
		options: ReaderOptions,
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
		format: BarcodeFormat,
		width: Int = 0,
		height: Int = 0,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1,
		setColor: Int = 0xff000000.toInt(),
		unsetColor: Int = 0xffffffff.toInt()
	): Bitmap = content.encode(
		format.toString(),
		width, height,
		addQuietZones, ecLevel
	).toBitmap(setColor, unsetColor)

	fun <T> encodeAsSvg(
		content: T,
		format: BarcodeFormat,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): String = content.encode(
		format.toString(),
		0, 0,
		addQuietZones, ecLevel
	).toSvg()

	fun <T> encodeAsText(
		content: T,
		format: BarcodeFormat,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1,
		inverted: Boolean = false
	): String = content.encode(
		format.toString(),
		0, 0,
		addQuietZones, ecLevel
	).toText(inverted)

	fun <T> T.encode(
		format: String,
		width: Int = 0,
		height: Int = 0,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): BitMatrix = when (this) {
		is String -> encodeString(
			this, format, width, height, addQuietZones, ecLevel
		)

		is ByteArray -> encodeByteArray(
			this, format, width, height, addQuietZones, ecLevel
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
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): BitMatrix

	private external fun encodeByteArray(
		bytes: ByteArray,
		format: String,
		width: Int = 0,
		height: Int = 0,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): BitMatrix

	private external fun encodeStringAsSvg(
		text: String,
		format: String,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): String

	private external fun encodeByteArrayAsSvg(
		bytes: ByteArray,
		format: String,
		addQuietZones: Boolean = true,
		ecLevel: Int = -1
	): String

	init {
		System.loadLibrary("zxingcpp_android")
	}
}
