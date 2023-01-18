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

package com.example.zxingcppdemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.DecodeHints
import de.markusfisch.android.zxingcpp.ZxingCpp.Format
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
	private val executor = Executors.newSingleThreadExecutor()
	private val permissions = listOf(Manifest.permission.CAMERA)
	private val beeper = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
	private val decodeHints = DecodeHints()

	private lateinit var viewFinder: PreviewView
	private lateinit var overlayView: OverlayView
	private lateinit var resultView: TextView
	private lateinit var fpsView: TextView
	private lateinit var chipJava: CheckBox
	private lateinit var chipQrCode: CheckBox
	private lateinit var chipTryHarder: CheckBox
	private lateinit var chipTryRotate: CheckBox
	private lateinit var chipTryInvert: CheckBox
	private lateinit var chipTryDownscale: CheckBox
	private lateinit var chipCrop: CheckBox
	private lateinit var chipPause: CheckBox

	private var lastText = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		viewFinder = findViewById(R.id.view_finder)
		overlayView = findViewById(R.id.detector)
		resultView = findViewById(R.id.text_result)
		fpsView = findViewById(R.id.text_fps)
		chipJava = findViewById(R.id.chip_java)
		chipQrCode = findViewById(R.id.chip_qrcode)
		chipTryHarder = findViewById(R.id.chip_tryHarder)
		chipTryRotate = findViewById(R.id.chip_tryRotate)
		chipTryInvert = findViewById(R.id.chip_tryInvert)
		chipTryDownscale = findViewById(R.id.chip_tryDownscale)
		chipCrop = findViewById(R.id.chip_crop)
		chipPause = findViewById(R.id.chip_pause)
	}

	private fun bindCameraUseCases() = viewFinder.post {
		val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
		cameraProviderFuture.addListener({
			// Set up the view finder use case to display camera preview.
			val preview = Preview.Builder()
				.setTargetAspectRatio(AspectRatio.RATIO_16_9)
				.build()

			// Set up the image analysis use case which will process frames
			// in real time.
			val imageAnalysis = ImageAnalysis.Builder()
				.setTargetAspectRatio(AspectRatio.RATIO_16_9)
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build()

			val imageSize = ImageSize()
			val viewRect = Rect()
			val cropRect = Rect()
			val readerJava = MultiFormatReader()

			var frameCounter = 0
			var lastFpsTimestamp = System.currentTimeMillis()
			var runtimes = 0L

			imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->
				if (chipPause.isChecked) {
					image.close()
					return@Analyzer
				}

				imageSize.set(
					image.width, image.height, image.imageInfo.rotationDegrees
				)
				viewRect.setCentered(
					viewFinder.width, viewFinder.height,
					imageSize.upright.x, imageSize.upright.y
				)
				cropRect.update(image.width, image.height)

				val useJava = chipJava.isChecked
				overlayView.updateTransformationMatrix(
					imageSize,
					viewRect,
					cropRect,
					useJava
				)

				val startTime = System.currentTimeMillis()
				val resultText = if (useJava) {
					readerJava.scan(image, cropRect)
				} else {
					scanCpp(image, cropRect)
				}

				runtimes += System.currentTimeMillis() - startTime

				var infoText: String? = null
				if (++frameCounter == 15) {
					val now = System.currentTimeMillis()
					val fps = 1000 * frameCounter.toDouble() /
							(now - lastFpsTimestamp)
					infoText = "Time: %2dms, FPS: %.02f, (%dx%d)"
						.format(
							runtimes / frameCounter,
							fps,
							image.width,
							image.height
						)
					lastFpsTimestamp = now
					frameCounter = 0
					runtimes = 0
				}

				showResult(resultText, infoText)
			})

			// Create a new camera selector each time, enforcing lens facing.
			val cameraSelector = CameraSelector.Builder().requireLensFacing(
				CameraSelector.LENS_FACING_BACK
			).build()

			// Camera provider is now guaranteed to be available.
			val cameraProvider = cameraProviderFuture.get()

			// Apply declared configs to CameraX using the same lifecycle owner.
			cameraProvider.unbindAll()
			cameraProvider.bindToLifecycle(
				this as LifecycleOwner, cameraSelector, preview, imageAnalysis
			)

			// Use the camera object to link our preview use case with
			// the view.
			preview.setSurfaceProvider(viewFinder.surfaceProvider)
		}, ContextCompat.getMainExecutor(this))
	}

	private fun Rect.update(width: Int, height: Int) {
		if (chipCrop.isChecked) {
			val cropSize = height / 3 * 2
			val left = (width - cropSize) / 2
			val top = (height - cropSize) / 2
			set(
				left,
				top,
				left + cropSize,
				top + cropSize
			)
		} else {
			set(0, 0, width, height)
		}
	}

	private fun MultiFormatReader.scan(image: ImageProxy, cropRect: Rect): String {
		val yPlane = image.planes[0]
		val yBuffer = yPlane.buffer
		val yStride = yPlane.rowStride
		val yHeight = image.height
		val data = ByteArray(yBuffer.remaining())
		yBuffer.get(data, 0, data.size)
		image.close()

		val hints = mutableMapOf<DecodeHintType, Any>()
		if (chipQrCode.isChecked) {
			hints[DecodeHintType.POSSIBLE_FORMATS] = arrayListOf(BarcodeFormat.QR_CODE)
		}
		if (chipTryHarder.isChecked) {
			hints[DecodeHintType.TRY_HARDER] = true
		}

		return try {
			val bitmap = BinaryBitmap(
				HybridBinarizer(
					PlanarYUVLuminanceSource(
						data, yStride, yHeight,
						cropRect.left, cropRect.top,
						cropRect.width(), cropRect.height(),
						false
					)
				)
			)
			decode(bitmap, hints)?.let {
				overlayView.show(it.resultPoints)
				"${it.barcodeFormat}: ${it.text}"
			} ?: ""
		} catch (e: Throwable) {
			// ZXing throws an exception when no barcode could be found.
			val s = e.toString()
			if (s != "com.google.zxing.NotFoundException") {
				s
			} else {
				""
			}
		}
	}

	private fun scanCpp(image: ImageProxy, cropRect: Rect): String = try {
		val yPlane = image.planes[0]
		decodeHints.apply {
			tryHarder = chipTryHarder.isChecked
			tryRotate = chipTryRotate.isChecked
			tryInvert = chipTryInvert.isChecked
			tryDownscale = chipTryDownscale.isChecked
			formats = if (chipQrCode.isChecked) {
				setOf(Format.QRCode)
			} else {
				setOf()
			}.joinToString()
		}
		ZxingCpp.readYBuffer(
			yPlane.buffer,
			yPlane.rowStride,
			cropRect,
			image.imageInfo.rotationDegrees,
			decodeHints
		)?.let {
			overlayView.show(it.position)
			"${it.format}: ${it.text}"
		} ?: ""
	} catch (e: Throwable) {
		e.message ?: "Error"
	} finally {
		image.close()
	}

	private fun showResult(
		resultText: String,
		fpsText: String?
	) = viewFinder.post {
		resultView.apply {
			text = resultText
			visibility = View.VISIBLE
		}
		overlayView.invalidate()

		if (fpsText != null) {
			fpsView.text = fpsText
		}

		if (resultText.isNotEmpty() && lastText != resultText) {
			lastText = resultText
			beeper.startTone(ToneGenerator.TONE_PROP_BEEP)
		}
	}

	override fun onResume() {
		super.onResume()

		// Request permissions each time the app resumes, since they can
		// be revoked at any time.
		if (!hasPermissions(this)) {
			ActivityCompat.requestPermissions(
				this,
				permissions.toTypedArray(),
				PERMISSION_REQUEST_CODE
			)
		} else {
			bindCameraUseCases()
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray,
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == PERMISSION_REQUEST_CODE && hasPermissions(this)) {
			bindCameraUseCases()
		} else {
			// If we don't have the required permissions, we can't run.
			finish()
		}
	}

	private fun hasPermissions(context: Context) = permissions.all {
		ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}

	companion object {
		private const val PERMISSION_REQUEST_CODE = 1
	}
}

class ImageSize {
	var native = Point()
	var upright = Point()
	var rotation: Int = 0

	fun set(width: Int, height: Int, rotation: Int) {
		native.set(width, height)
		upright.apply {
			when (rotation) {
				90, 270 -> set(height, width)
				else -> set(width, height)
			}
		}
		this.rotation = rotation
	}
}

// Calculate the centered image rectangle in the view rectangle.
private fun Rect.setCentered(
	viewWidth: Int, viewHeight: Int,
	imageWidth: Int, imageHeight: Int
) {
	var surfaceWidth = imageWidth
	var surfaceHeight = imageHeight
	if (viewWidth * surfaceWidth > viewHeight * surfaceHeight) {
		surfaceWidth = surfaceWidth * viewHeight / surfaceHeight
		surfaceHeight = viewHeight
	} else {
		surfaceHeight = surfaceHeight * viewWidth / surfaceWidth
		surfaceWidth = viewWidth
	}
	val left = (viewWidth - surfaceWidth) / 2
	val top = (viewHeight - surfaceHeight) / 2
	set(
		left,
		top,
		left + surfaceWidth,
		top + surfaceHeight
	)
}
