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

#include "BitMatrix.h"
#include "CharacterSet.h"
#include "GTIN.h"
#include "JNIUtils.h"
#include "ReadBarcode.h"
#include "MultiFormatWriter.h"
#include "Utf.h"

#include <android/bitmap.h>
#include <chrono>
#include <exception>
#include <stdexcept>

using namespace ZXing;

static const char* JavaBarcodeFormatName(BarcodeFormat format)
{
	switch (format) {
	case BarcodeFormat::None: return "NONE";
	case BarcodeFormat::Aztec: return "AZTEC";
	case BarcodeFormat::Codabar: return "CODABAR";
	case BarcodeFormat::Code39: return "CODE_39";
	case BarcodeFormat::Code93: return "CODE_93";
	case BarcodeFormat::Code128: return "CODE_128";
	case BarcodeFormat::DataMatrix: return "DATA_MATRIX";
	case BarcodeFormat::EAN8: return "EAN_8";
	case BarcodeFormat::EAN13: return "EAN_13";
	case BarcodeFormat::ITF: return "ITF";
	case BarcodeFormat::MaxiCode: return "MAXICODE";
	case BarcodeFormat::PDF417: return "PDF_417";
	case BarcodeFormat::QRCode: return "QR_CODE";
	case BarcodeFormat::MicroQRCode: return "MICRO_QR_CODE";
	case BarcodeFormat::DataBar: return "DATA_BAR";
	case BarcodeFormat::DataBarExpanded: return "DATA_BAR_EXPANDED";
	case BarcodeFormat::UPCA: return "UPC_A";
	case BarcodeFormat::UPCE: return "UPC_E";
	default: throw std::invalid_argument("Invalid format");
	}
}

static const char* JavaContentTypeName(ContentType contentType)
{
	switch (contentType) {
	case ContentType::Text: return "TEXT";
	case ContentType::Binary: return "BINARY";
	case ContentType::Mixed: return "MIXED";
	case ContentType::GS1: return "GS1";
	case ContentType::ISO15434: return "ISO15434";
	case ContentType::UnknownECI: return "UNKNOWN_ECI";
	default: throw std::invalid_argument("Invalid contentType");
	}
}

static EanAddOnSymbol EanAddOnSymbolFromString(const std::string& name)
{
	if (name == "IGNORE") {
		return EanAddOnSymbol::Ignore;
	} else if (name == "READ") {
		return EanAddOnSymbol::Read;
	} else if (name == "REQUIRE") {
		return EanAddOnSymbol::Require;
	} else {
		throw std::invalid_argument("Invalid eanAddOnSymbol name");
	}
}

static Binarizer BinarizerFromString(const std::string& name)
{
	if (name == "LOCAL_AVERAGE") {
		return Binarizer::LocalAverage;
	} else if (name == "GLOBAL_HISTOGRAM") {
		return Binarizer::GlobalHistogram;
	} else if (name == "FIXED_THRESHOLD") {
		return Binarizer::FixedThreshold;
	} else if (name == "BOOL_CAST") {
		return Binarizer::BoolCast;
	} else {
		throw std::invalid_argument("Invalid binarizer name");
	}
}

static TextMode TextModeFromString(const std::string& name)
{
	if (name == "PLAIN") {
		return TextMode::Plain;
	} else if (name == "ECI") {
		return TextMode::ECI;
	} else if (name == "HRI") {
		return TextMode::HRI;
	} else if (name == "HEX") {
		return TextMode::Hex;
	} else if (name == "ESCAPED") {
		return TextMode::Escaped;
	} else {
		throw std::invalid_argument("Invalid textMode name");
	}
}

static jobject ThrowJavaException(JNIEnv* env, const char* message)
{
	jclass jcls = env->FindClass("java/lang/RuntimeException");
	env->ThrowNew(jcls, message);
	return nullptr;
}

static jbyteArray CreateByteArray(JNIEnv* env, const void* data,
	unsigned int length)
{
	auto size = static_cast<jsize>(length);
	jbyteArray byteArray = env->NewByteArray(size);
	env->SetByteArrayRegion(
		byteArray, 0, size, reinterpret_cast<const jbyte*>(data));
	return byteArray;
}

static jbyteArray CreateByteArray(JNIEnv* env,
	const std::vector<uint8_t>& byteArray)
{
	return CreateByteArray(env, reinterpret_cast<const void*>(byteArray.data()),
		byteArray.size());
}

static jobject CreateBitMatrix(JNIEnv* env, int width, int height,
	jbyteArray data)
{
	jclass cls = env->FindClass("de/markusfisch/android/zxingcpp/ZxingCpp$BitMatrix");
	auto constructor = env->GetMethodID(
		cls, "<init>", "(II[B)V");
	return env->NewObject(cls, constructor, width, height, data);
}

static jobject CreateBitMatrix(JNIEnv* env, const Matrix<uint8_t>& bm)
{
	return CreateBitMatrix(env, bm.width(), bm.height(),
		CreateByteArray(env, bm.data(), bm.size()));
}

static jobject CreateGTIN(JNIEnv* env, const std::string& country,
	const std::string& addOn, const std::string& price,
	const std::string& issueNumber)
{
	jclass cls = env->FindClass("de/markusfisch/android/zxingcpp/ZxingCpp$GTIN");
	auto constructor = env->GetMethodID(
		cls, "<init>",
		"(Ljava/lang/String;"
			"Ljava/lang/String;"
			"Ljava/lang/String;"
			"Ljava/lang/String;)V");
	return env->NewObject(
		cls, constructor,
		C2JString(env, country),
		C2JString(env, addOn),
		C2JString(env, price),
		C2JString(env, issueNumber));
}

static jobject CreateOptionalGTIN(JNIEnv* env, const Result& result)
{
	auto country = GTIN::LookupCountryIdentifier(
		result.text(TextMode::Plain),
		result.format());
	auto addOn = GTIN::EanAddOn(result);
	return country.empty()
		? nullptr
		: CreateGTIN(
			env,
			country,
			addOn,
			GTIN::Price(addOn),
			GTIN::IssueNr(addOn));
}

static jobject CreateAndroidPoint(JNIEnv* env, const PointT<int>& point)
{
	jclass cls = env->FindClass("android/graphics/Point");
	auto constructor = env->GetMethodID(cls, "<init>", "(II)V");
	return env->NewObject(cls, constructor, point.x, point.y);
}

static jobject CreatePosition(JNIEnv* env, const Position& position)
{
	jclass cls = env->FindClass(
		"de/markusfisch/android/zxingcpp/ZxingCpp$Position");
	auto constructor = env->GetMethodID(
		cls, "<init>",
		"(Landroid/graphics/Point;"
		"Landroid/graphics/Point;"
		"Landroid/graphics/Point;"
		"Landroid/graphics/Point;"
		"D)V");
	return env->NewObject(
		cls, constructor,
		CreateAndroidPoint(env, position.topLeft()),
		CreateAndroidPoint(env, position.topRight()),
		CreateAndroidPoint(env, position.bottomLeft()),
		CreateAndroidPoint(env, position.bottomRight()),
		position.orientation());
}

static jobject CreateContentType(JNIEnv* env, ContentType contentType)
{
	jclass cls = env->FindClass(
		"de/markusfisch/android/zxingcpp/ZxingCpp$ContentType");
	jfieldID fidCT = env->GetStaticFieldID(cls,
		JavaContentTypeName(contentType),
		"Lde/markusfisch/android/zxingcpp/ZxingCpp$ContentType;");
	return env->GetStaticObjectField(cls, fidCT);
}

static jobject CreateResult(JNIEnv* env, const Result& result)
{
	jclass cls = env->FindClass(
		"de/markusfisch/android/zxingcpp/ZxingCpp$Result");
	auto constructor = env->GetMethodID(
		cls, "<init>",
		"(Ljava/lang/String;"
		"Lde/markusfisch/android/zxingcpp/ZxingCpp$ContentType;"
		"Ljava/lang/String;"
		"Lde/markusfisch/android/zxingcpp/ZxingCpp$Position;"
		"I"
		"[B"
		"Ljava/lang/String;"
		"Ljava/lang/String;"
		"I"
		"I"
		"Ljava/lang/String;"
		"Z"
		"I"
		"Ljava/lang/String;"
		"Lde/markusfisch/android/zxingcpp/ZxingCpp$GTIN;)V");
	return env->NewObject(
		cls, constructor,
		C2JString(env, JavaBarcodeFormatName(result.format())),
		CreateContentType(env, result.contentType()),
		C2JString(env, result.text()),
		CreatePosition(env, result.position()),
		result.orientation(),
		CreateByteArray(env, result.bytes()),
		C2JString(env, result.ecLevel()),
		C2JString(env, result.symbologyIdentifier()),
		result.sequenceSize(),
		result.sequenceIndex(),
		C2JString(env, result.sequenceId()),
		result.readerInit(),
		result.lineCount(),
		C2JString(env, result.version()),
		CreateOptionalGTIN(env, result));
}

static jobject Read(JNIEnv* env, ImageView image, DecodeHints decodeHints)
{
	try {
		auto results = ReadBarcodes(image, decodeHints);
		if (!results.empty()) {
			// Only allocate when something is found.
			auto cls = env->FindClass("java/util/ArrayList");
			auto list = env->NewObject(cls,
				env->GetMethodID(cls, "<init>", "()V"));
			auto add = env->GetMethodID(cls, "add", "(Ljava/lang/Object;)Z");
			for (const auto& result: results) {
				env->CallBooleanMethod(list, add, CreateResult(env, result));
			}
			return list;
		} else {
			return nullptr;
		}
	} catch (const std::exception& e) {
		return ThrowJavaException(env, e.what());
	} catch (...) {
		return ThrowJavaException(env, "Unknown exception");
	}
}

static bool GetBooleanField(JNIEnv* env, jclass cls, jobject hints,
	const char* name)
{
	return env->GetBooleanField(hints, env->GetFieldID(cls, name, "Z"));
}

static int GetIntField(JNIEnv* env, jclass cls, jobject hints,
	const char* name)
{
	return env->GetIntField(hints, env->GetFieldID(cls, name, "I"));
}

static std::string GetStringField(JNIEnv* env, jclass cls, jobject hints,
	const char* name)
{
	jfieldID fid = env->GetFieldID(cls, name, "Ljava/lang/String;");
	auto str = (jstring) env->GetObjectField(hints, fid);
	return J2CString(env, str);
}

static std::string GetEnumField(JNIEnv* env, jclass hintClass, jobject hints,
	const char* enumClass, const char* name)
{
	jclass cls = env->FindClass(enumClass);
	jstring s = (jstring) env->CallObjectMethod(
		env->GetObjectField(hints, env->GetFieldID(hintClass, name,
			("L" + std::string(enumClass) + ";").c_str())),
		env->GetMethodID(cls, "name", "()Ljava/lang/String;"));
	return J2CString(env, s);
}

static DecodeHints CreateDecodeHints(JNIEnv* env, jobject hints)
{
	jclass cls = env->GetObjectClass(hints);
	return DecodeHints()
		.setFormats(BarcodeFormatsFromString(
			GetStringField(env, cls, hints, "formats")))
		.setTryHarder(GetBooleanField(env, cls, hints, "tryHarder"))
		.setTryRotate(GetBooleanField(env, cls, hints, "tryRotate"))
		.setTryInvert(GetBooleanField(env, cls, hints, "tryInvert"))
		.setTryDownscale(GetBooleanField(env, cls, hints, "tryDownscale"))
		.setIsPure(GetBooleanField(env, cls, hints, "isPure"))
		.setTryCode39ExtendedMode(GetBooleanField(env, cls, hints, "tryCode39ExtendedMode"))
		.setValidateCode39CheckSum(GetBooleanField(env, cls, hints, "validateCode39CheckSum"))
		.setValidateITFCheckSum(GetBooleanField(env, cls, hints, "validateITFCheckSum"))
		.setReturnCodabarStartEnd(GetBooleanField(env, cls, hints, "returnCodabarStartEnd"))
		.setReturnErrors(GetBooleanField(env, cls, hints, "returnErrors"))
		.setDownscaleFactor(GetIntField(env, cls, hints, "downscaleFactor"))
		.setEanAddOnSymbol(EanAddOnSymbolFromString(GetEnumField(env, cls, hints,
			"de/markusfisch/android/zxingcpp/ZxingCpp$EanAddOnSymbol",
			"eanAddOnSymbol")))
		.setBinarizer(BinarizerFromString(GetEnumField(env, cls, hints,
			"de/markusfisch/android/zxingcpp/ZxingCpp$Binarizer",
			"binarizer")))
		.setTextMode(TextModeFromString(GetEnumField(env, cls, hints,
			"de/markusfisch/android/zxingcpp/ZxingCpp$TextMode",
			"textMode")))
		.setMinLineCount(GetIntField(env, cls, hints, "minLineCount"))
		.setMaxNumberOfSymbols(GetIntField(env, cls, hints, "maxNumberOfSymbols"))
		.setDownscaleThreshold(GetIntField(env, cls, hints, "downscaleThreshold"));
}

extern "C" JNIEXPORT jobject JNICALL
Java_de_markusfisch_android_zxingcpp_ZxingCpp_readYBuffer(
	JNIEnv* env, jobject, jobject yBuffer, jint rowStride,
	jint left, jint top, jint width, jint height, jint rotation,
	jobject hints)
{
	const uint8_t* pixels = static_cast<uint8_t*>(
		env->GetDirectBufferAddress(yBuffer));

	auto image = ImageView{
		pixels + top * rowStride + left,
		width,
		height,
		ImageFormat::Lum,
		rowStride
	}.rotated(rotation);

	return Read(env, image, CreateDecodeHints(env, hints));
}

extern "C" JNIEXPORT jobject JNICALL
Java_de_markusfisch_android_zxingcpp_ZxingCpp_readByteArray(
	JNIEnv* env, jobject, jbyteArray yuvData, jint rowStride,
	jint left, jint top, jint width, jint height, jint rotation,
	jobject hints)
{
	auto *pixels = env->GetByteArrayElements(yuvData, nullptr);

	auto image = ImageView{
		reinterpret_cast<uint8_t*>(pixels) + top * rowStride + left,
		width,
		height,
		ImageFormat::Lum,
		rowStride
	}.rotated(rotation);

	auto result = Read(env, image, CreateDecodeHints(env, hints));
	env->ReleaseByteArrayElements(yuvData, pixels, 0);

	return result;
}

struct LockedPixels
{
	JNIEnv* env;
	jobject bitmap;
	void* pixels = nullptr;

	LockedPixels(JNIEnv* env, jobject bitmap) : env(env), bitmap(bitmap) {
		if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESUT_SUCCESS) {
			pixels = nullptr;
		}
	}

	operator const uint8_t*() const {
		return static_cast<const uint8_t*>(pixels);
	}

	virtual ~LockedPixels() {
		if (pixels) {
			AndroidBitmap_unlockPixels(env, bitmap);
		}
	}
};

extern "C" JNIEXPORT jobject JNICALL
Java_de_markusfisch_android_zxingcpp_ZxingCpp_readBitmap(
	JNIEnv* env, jobject, jobject bitmap,
	jint left, jint top, jint width, jint height, jint rotation,
	jobject hints)
{
	AndroidBitmapInfo bmInfo;
	AndroidBitmap_getInfo(env, bitmap, &bmInfo);

	ImageFormat fmt;
	switch (bmInfo.format) {
	case ANDROID_BITMAP_FORMAT_A_8: fmt = ImageFormat::Lum; break;
	case ANDROID_BITMAP_FORMAT_RGBA_8888: fmt = ImageFormat::RGBX; break;
	default: return ThrowJavaException(env, "Unsupported format");
	}

	auto pixels = LockedPixels(env, bitmap);

	if (!pixels) {
		return ThrowJavaException(env, "Failed to lock/Read AndroidBitmap data");
	}

	auto image = ImageView{
		pixels,
		(int)bmInfo.width,
		(int)bmInfo.height,
		fmt,
		(int)bmInfo.stride
	}.cropped(left, top, width, height).rotated(rotation);

	return Read(env, image, CreateDecodeHints(env, hints));
}

static jobject Encode(JNIEnv* env, const std::string& content, CharacterSet encoding,
	jstring format, jint width, jint height, jint margin, jint eccLevel)
{
	try {
		auto writer = MultiFormatWriter(BarcodeFormatFromString(J2CString(env, format)))
			.setEncoding(encoding)
			.setMargin(margin)
			.setEccLevel(eccLevel);
		// Avoid MultiFormatWriter.encode(std::string,…)
		// because it ignores encoding and always runs
		// FromUtf8() which mangles binary content.
		std::wstring ws = encoding == CharacterSet::UTF8
			? FromUtf8(content)
			: std::wstring(content.begin(), content.end());
		auto bm = writer.encode(ws, width, height);
		return CreateBitMatrix(env, ToMatrix<uint8_t>(bm));
	} catch (const std::exception& e) {
		ThrowJavaException(env, e.what());
		return nullptr;
	}
}

extern "C" JNIEXPORT jobject JNICALL
Java_de_markusfisch_android_zxingcpp_ZxingCpp_encodeString(
	JNIEnv* env, jobject, jstring text, jstring format,
	jint width, jint height, jint margin, jint eccLevel)
{
	return Encode(env, J2CString(env, text), CharacterSet::UTF8,
		format, width, height, margin, eccLevel);
}

extern "C" JNIEXPORT jobject JNICALL
Java_de_markusfisch_android_zxingcpp_ZxingCpp_encodeByteArray(
	JNIEnv* env, jobject, jbyteArray data, jstring format,
	jint width, jint height, jint margin, jint eccLevel)
{
	auto bytes = env->GetByteArrayElements(data, nullptr);
	auto bitmap = Encode(env,
		std::string(
			reinterpret_cast<const char*>(bytes),
			env->GetArrayLength(data)),
		CharacterSet::BINARY,
		format, width, height, margin, eccLevel);
	env->ReleaseByteArrayElements(data, bytes, 0);
	return bitmap;
}
