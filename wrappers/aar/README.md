# ZXing-C++ Android Library

This library makes the C++ fork of the
[ZXing](https://github.com/zxing/zxing) barcode scanning library
available for Android in a simple and flexible way.

It tries to be a full replacement of the original Java implementation and
provides (almost) the same result metadata as the orignal Java library.
The generation of barcodes is also supported.

## Why using this over the offical wrapper?

Unlike the official Android
[wrapper](https://github.com/nu-book/zxing-cpp/tree/master/wrappers/android),
it neither includes nor requires CameraX and can be used with the legacy
camera API, Camera2 or CameraX. It can also be used down to API level 9.

This fork exists because these things are important to me and the apps
I build. I just can't and don't want to use CameraX everywhere and I
don't want to exclude people with old devices.

## Why using this over the Java implementation?

The main issue with the original Java implementation is that it is in
"Maintenance Mode Only", which means no new features or improvements
or barcode formats are accepted.

Another reason is speed, as the C++ fork is much faster than the Java
implementation.

## How to include

### JitPack

Add the JitPack repository in your root `build.gradle` at the end of
repositories:

```groovy
allprojects {
	repositories {
		…
		maven { url 'https://jitpack.io' }
	}
}
```

Then add the dependency in your `app/build.gradle`:

```groovy
dependencies {
	implementation 'com.github.markusfisch:zxing-cpp:v2.0.0.0'
}
```

### Proguard/R8

Don't forget to add the following line to your `app/proguard-rules.pro` if
you are using minification:

```
-keep class de.markusfisch.android.zxingcpp.** { *; }
```

### Build and use the AAR file yourself

Alternatively you can build the AAR (Android Archive) yourself:

```sh
$ ./gradlew :zxingcpp:assembleRelease
```

Then copy `zxingcpp/build/outputs/aar/zxingcpp-release.aar` into
`app/libs` of your app.

## How to decode barcodes

Have a look at [MainActivity.kt](app/src/main/java/com/example/zxingcppdemo/MainActivity.kt)
of the sample app for details.

### Legacy Camera API

Use [ZXingCpp.readByteArray][zxingcpp] with the byte array from
[onPreviewFrame][onPreviewFrame].
You can calculate the row stride with this [formula][rowStride].

```kotlin
camera.setPreviewCallback { frameData, _ ->
	val result: Result? = ZxingCpp.readByteArray(
		frameData,
		rowStride
	)
	// Now do something with result…
}
```

### Camera2/CameraX API

Use [ZXingCpp.readYBuffer][zxingcpp] with the Y plane buffer (at index 0)
from the [Image][image] (or [ImageProxy][imageProxy] in case of CameraX)
object.

```kotlin
imageReader.acquireLatestImage()?.use { image ->
	val yPlane = image.planes[0]
	val result: Result? = ZxingCpp.readYBuffer(
		yPlane.buffer,
		yPlane.rowStride
	)
	// Now do something with result…
}
```

### Static images

For static images you can use [ZXingCpp.readBitmap][zxingcpp].

```kotlin
val result: Result? = ZxingCpp.readBitmap(someBitmap)
```

## How to encode barcodes

To encode barcodes you can use:

* [ZXingCpp.encodeAsBitmap][zxingcpp]
* [ZXingCpp.encodeAsSvg][zxingcpp]
* [ZXingCpp.encodeAsText][zxingcpp]

All of them start with the same two (mandatory) arguments:

```kotlin
val bitmap = ZxingCpp.encodeAsBitmap(
	"text to encode",
	ZxingCpp.Format.QR_CODE
)
```

[zxingcpp]: zxingcpp/src/main/java/de/markusfisch/android/zxingcpp/ZxingCpp.kt
[onPreviewFrame]: https://developer.android.com/reference/android/hardware/Camera.PreviewCallback#onPreviewFrame(byte[],%20android.hardware.Camera)
[rowStride]: https://developer.android.com/reference/android/hardware/Camera.Parameters#setPreviewFormat(int)
[image]: https://developer.android.com/reference/android/media/Image
[imageProxy]: https://developer.android.com/reference/androidx/camera/core/ImageProxy
