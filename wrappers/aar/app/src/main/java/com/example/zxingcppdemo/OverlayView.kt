package com.example.zxingcppdemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.zxing.ResultPoint
import de.markusfisch.android.zxingcpp.ZxingCpp

class OverlayView : View {
	private val dp = context.resources.displayMetrics.density
	private val radius = 6f * dp
	private val dotPaint = paint(0xc0ffffff.toInt()).apply {
		style = Paint.Style.FILL
	}
	private val cropPaint = paint(0xffffffff.toInt()).apply {
		style = Paint.Style.STROKE
		strokeWidth = dp
	}
	private val cropRectInView = RectF()
	private val transformationMatrix = Matrix()
	private val coords = FloatArray(16)

	private var count = 0

	constructor(context: Context, attrs: AttributeSet) :
			super(context, attrs)

	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
			super(context, attrs, defStyleAttr)

	fun updateTransformationMatrix(
		imageSize: ImageSize,
		viewRect: Rect,
		cropRect: Rect,
		unrotated: Boolean
	) {
		count = 0
		cropRectInView.set(cropRect)
		transformationMatrix.apply {
			// Scale and rotate to map the crop rectangle from image
			// to view coordinates.
			setScale(1f / imageSize.native.x, 1f / imageSize.native.y)
			postRotate(imageSize.rotation.toFloat(), .5f, .5f)
			postScale(viewRect.width().toFloat(), viewRect.height().toFloat())
			postTranslate(viewRect.left.toFloat(), viewRect.top.toFloat())
			mapRect(cropRectInView)

			if (unrotated) {
				// Use this matrix for result points that aren't already
				// rotated too. We just need to translate the coordinates
				// for the crop rectangle first.
				preTranslate(
					cropRect.left.toFloat(),
					cropRect.top.toFloat()
				)
			} else {
				// If the result points are already rotated to match
				// the image rotation, just scale and translate them.
				setScale(
					viewRect.width().toFloat() / imageSize.upright.x,
					viewRect.height().toFloat() / imageSize.upright.y
				)
				postTranslate(cropRectInView.left, cropRectInView.top)
			}
		}
	}

	fun show(resultPoints: Array<ResultPoint>?) {
		count = 0
		resultPoints ?: return
		for (p in resultPoints) {
			coords[count++] = p.x
			coords[count++] = p.y
		}
		transformationMatrix.mapPoints(coords, count)
	}

	fun show(position: ZxingCpp.Position?) {
		count = 0
		position ?: return
		setOf(
			position.topLeft,
			position.topRight,
			position.bottomRight,
			position.bottomLeft
		).forEach {
			coords[count++] = it.x.toFloat()
			coords[count++] = it.y.toFloat()
		}
		transformationMatrix.mapPoints(coords, count)
	}

	override fun onDraw(canvas: Canvas) {
		canvas.apply {
			drawRect(cropRectInView, cropPaint)
			for (i in 0 until count step 2) {
				drawCircle(coords[i], coords[i + 1], radius, dotPaint)
			}
		}
	}
}

private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
	this.color = color
}

private fun Matrix.mapPoints(points: FloatArray, count: Int) {
	mapPoints(points, 0, points, 0, count)
}
