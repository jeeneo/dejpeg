package com.je.dejpeg

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

class BeforeAfterImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sliderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val sliderLineOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        alpha = 120
    }
    private val sliderHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.FILL
        setShadowLayer(18f, 0f, 2f, Color.argb(90, 0, 0, 0))
    }
    private val sliderHandleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 120, 120, 120)
        style = Paint.Style.FILL
    }
    private val sliderHandleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 40, 40, 40)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val arrowOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val buttonBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val buttonSize = 28f
    private val buttonPadding = 16f
    private val buttonRadius = 24f
    private val shareButtonBounds = RectF()
    private val saveButtonBounds = RectF()
    private var shareIcon: Drawable? = null
    private var saveIcon: Drawable? = null
    private var buttonCallback: ButtonCallback? = null
    
    private var sliderPosition = 0.5f
    private var isDraggingSlider = false
    private val sliderTouchRadius = 80f
    private val sliderHandleRadius = 36f
    private var showSlider = true
    
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    
    private var scaleFactor = 1.0f
    private var minScale = 1.0f
    private var maxScale = 5.0f
    private var translateX = 0f
    private var translateY = 0f
    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    
    private var isScaling = false
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f
    
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    private val imageBounds = RectF()
    private val viewBounds = RectF()
    
    private var vibrationManager: VibrationManager? = null
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        shareIcon = context.getDrawable(R.drawable.ic_share)?.apply {
            setTint(Color.WHITE)
        }
        saveIcon = context.getDrawable(R.drawable.ic_save)?.apply {
            setTint(Color.WHITE)
        }
    }

    interface ButtonCallback {
        fun onShareClicked()
        fun onSaveClicked()
    }

    fun setButtonCallback(callback: ButtonCallback) {
        buttonCallback = callback
    }
    
    fun setBeforeImage(bitmap: Bitmap) {
        beforeBitmap = bitmap
        calculateImageBounds()
        invalidate()
    }
    
    fun setAfterImage(bitmap: Bitmap?) {
        afterBitmap = bitmap
        showSlider = bitmap != null
        calculateImageBounds()
        invalidate()
    }

    fun clearImages() {
        beforeBitmap = null
        afterBitmap = null
        showSlider = false
        invalidate()
    }
    
    fun setVibrationManager(manager: VibrationManager) {
        vibrationManager = manager
    }
    
    private fun calculateImageBounds() {
        val bitmap = beforeBitmap ?: afterBitmap ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth == 0f || viewHeight == 0f) return

        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        minScale = min(scaleX, scaleY)

        val scaledWidth = imageWidth * minScale
        val scaledHeight = imageHeight * minScale

        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2

        imageBounds.set(0f, 0f, imageWidth, imageHeight)
        viewBounds.set(left, top, left + scaledWidth, top + scaledHeight)

        resetTransform()
    }

    private fun resetTransform() {
        scaleFactor = minScale
        val viewCenterX = width / 2f
        val viewCenterY = height / 2f
        val imageCenterX = imageBounds.centerX() * scaleFactor
        val imageCenterY = imageBounds.centerY() * scaleFactor
        translateX = viewCenterX - imageCenterX
        translateY = viewCenterY - imageCenterY
        updateMatrix()
    }

    private fun updateMatrix() {
        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(translateX, translateY)

        matrix.invert(inverseMatrix)

        constrainTranslation()

        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(translateX, translateY)

        matrix.invert(inverseMatrix)

        invalidate()
    }

    private fun constrainTranslation() {
        val bitmap = beforeBitmap ?: afterBitmap ?: return

        val scaledWidth = bitmap.width * scaleFactor
        val scaledHeight = bitmap.height * scaleFactor

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (scaledWidth <= viewWidth) {
            translateX = (viewWidth - scaledWidth) / 2f
        } else {
            val minTransX = viewWidth - scaledWidth
            val maxTransX = 0f
            translateX = translateX.coerceIn(minTransX, maxTransX)
        }

        if (scaledHeight <= viewHeight) {
            translateY = (viewHeight - scaledHeight) / 2f
        } else {
            val minTransY = viewHeight - scaledHeight
            val maxTransY = 0f
            translateY = translateY.coerceIn(minTransY, maxTransY)
        }
    }
    
    fun hasOnlyBeforeImage(): Boolean {
        return beforeBitmap != null && afterBitmap == null
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageBounds()
        
        val density = resources.displayMetrics.density
        val buttonSizePx = buttonSize * density
        val buttonPaddingPx = buttonPadding * density
        
        saveButtonBounds.set(
            width - buttonPaddingPx - buttonSizePx,
            height - buttonPaddingPx - buttonSizePx,
            width - buttonPaddingPx,
            height - buttonPaddingPx
        )
        
        shareButtonBounds.set(
            width - buttonPaddingPx * 2 - buttonSizePx * 2,
            height - buttonPaddingPx - buttonSizePx,
            width - buttonPaddingPx * 2 - buttonSizePx,
            height - buttonPaddingPx
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (beforeBitmap == null) return

        canvas.save()
        canvas.concat(matrix)

        if (!showSlider || afterBitmap == null) {
            beforeBitmap?.let {
                canvas.drawBitmap(it, 0f, 0f, paint)
            }
        } else {
            afterBitmap?.let {
                canvas.save()
                canvas.clipRect(it.width * sliderPosition, 0f, it.width.toFloat(), it.height.toFloat())
                canvas.drawBitmap(it, 0f, 0f, paint)
                canvas.restore()
            }

            beforeBitmap?.let {
                canvas.save()
                canvas.clipRect(0f, 0f, it.width * sliderPosition, it.height.toFloat())
                canvas.drawBitmap(it, 0f, 0f, paint)
                canvas.restore()
            }
        }

        canvas.restore()

        if (showSlider && afterBitmap != null) {
            drawSlider(canvas)
        }

        if (afterBitmap != null) {
            canvas.drawRoundRect(shareButtonBounds, buttonRadius, buttonRadius, buttonBackgroundPaint)
            canvas.drawRoundRect(saveButtonBounds, buttonRadius, buttonRadius, buttonBackgroundPaint)

            shareIcon?.let { icon: Drawable ->
                val padding = buttonSize * 0.25f * resources.displayMetrics.density
                icon.setBounds(
                    (shareButtonBounds.left + padding).toInt(),
                    (shareButtonBounds.top + padding).toInt(),
                    (shareButtonBounds.right - padding).toInt(),
                    (shareButtonBounds.bottom - padding).toInt()
                )
                icon.draw(canvas)
            }

            saveIcon?.let { icon ->
                val padding = buttonSize * 0.25f * resources.displayMetrics.density
                icon.setBounds(
                    (saveButtonBounds.left + padding).toInt(),
                    (saveButtonBounds.top + padding).toInt(),
                    (saveButtonBounds.right - padding).toInt(),
                    (saveButtonBounds.bottom - padding).toInt()
                )
                icon.draw(canvas)
            }
        }
    }
    
    private fun drawSlider(canvas: Canvas) {
        val bitmap = beforeBitmap ?: afterBitmap ?: return

        val points = floatArrayOf(bitmap.width * sliderPosition, 0f, bitmap.width * sliderPosition, bitmap.height.toFloat())
        matrix.mapPoints(points)

        val sliderX = points[0]
        val topY = points[1]
        val bottomY = points[3]

        canvas.drawLine(sliderX, topY, sliderX, bottomY, sliderLineOutlinePaint)
        canvas.drawLine(sliderX, topY, sliderX, bottomY, sliderPaint)

        val handleY = (topY + bottomY) / 2
        canvas.drawCircle(sliderX, handleY, sliderHandleRadius, sliderHandlePaint)

        val leftArrowPath = Path().apply {
            moveTo(sliderX - 14f, handleY)
            lineTo(sliderX - 22f, handleY - 10f)
            lineTo(sliderX - 22f, handleY + 10f)
            close()
        }
        val rightArrowPath = Path().apply {
            moveTo(sliderX + 14f, handleY)
            lineTo(sliderX + 22f, handleY - 10f)
            lineTo(sliderX + 22f, handleY + 10f)
            close()
        }
        canvas.drawPath(leftArrowPath, arrowOutlinePaint)
        canvas.drawPath(rightArrowPath, arrowOutlinePaint)
        canvas.drawPath(leftArrowPath, arrowPaint)
        canvas.drawPath(rightArrowPath, arrowPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (afterBitmap != null) {
                    if (shareButtonBounds.contains(x, y)) {
                        buttonCallback?.onShareClicked()
                        return true
                    }
                    if (saveButtonBounds.contains(x, y)) {
                        buttonCallback?.onSaveClicked()
                        return true
                    }
                }
            }
        }
        scaleGestureDetector.onTouchEvent(event)

        if (isScaling) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isScaling = false
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    isDraggingSlider = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        gestureDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
                if (isTouchingSlider(event.x, event.y)) {
                    isDraggingSlider = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    val index = event.actionIndex
                    lastTouchX = event.getX(index)
                    lastTouchY = event.getY(index)
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    
                    if (isDraggingSlider) {
                        updateSliderPosition(x)
                    } else if (scaleFactor > minScale) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        
                        translateX += dx
                        translateY += dy
                        updateMatrix()
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDraggingSlider = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        
        return true
    }
    
    private fun isTouchingSlider(x: Float, y: Float): Boolean {
        if (!showSlider) return false
        val bitmap = beforeBitmap ?: afterBitmap ?: return false
        val points = floatArrayOf(bitmap.width * sliderPosition, 0f, bitmap.width * sliderPosition, bitmap.height.toFloat())
        matrix.mapPoints(points)

        val sliderX = points[0]
        val topY = points[1]
        val bottomY = points[3]

        val verticalMargin = 60f
        val withinX = kotlin.math.abs(x - sliderX) <= sliderTouchRadius
        val withinY = y in (topY - verticalMargin)..(bottomY + verticalMargin)
        if (withinX && withinY) {
            vibrationManager?.vibrateSliderChange()
        }
        return withinX && withinY
    }
    
    private fun updateSliderPosition(x: Float) {
        if (!showSlider) return
        
        val bitmap = beforeBitmap ?: afterBitmap ?: return
        
        val points = floatArrayOf(x, 0f)
        inverseMatrix.mapPoints(points)
        
        sliderPosition = (points[0] / bitmap.width).coerceIn(0f, 1f)
        vibrationManager?.vibrateSliderChange()
        invalidate()
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scaleFactor
            val newScaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val scaleChange = newScaleFactor / prevScale

            val focusX = detector.focusX
            val focusY = detector.focusY

            translateX = (translateX - focusX) * scaleChange + focusX
            translateY = (translateY - focusY) * scaleChange + focusY

            scaleFactor = newScaleFactor

            updateMatrix()
            return true
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val currentScale = scaleFactor
            val targetScale: Float
            val focusX = e.x
            val focusY = e.y

            if (currentScale > minScale) {
                targetScale = minScale
            } else {
                targetScale = maxScale
            }

            animateZoom(targetScale, focusX, focusY)
            return true
        }
    }

    private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
        val startScale = scaleFactor
        val startTranslateX = translateX
        val startTranslateY = translateY

        val scaleChange = targetScale / startScale

        val endTranslateX = (startTranslateX - focusX) * scaleChange + focusX
        val endTranslateY = (startTranslateY - focusY) * scaleChange + focusY

        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 250
        animator.addUpdateListener { animation ->
            val t = animation.animatedValue as Float
            scaleFactor = startScale + (targetScale - startScale) * t
            translateX = startTranslateX + (endTranslateX - startTranslateX) * t
            translateY = startTranslateY + (endTranslateY - startTranslateY) * t
            updateMatrix()
        }
        animator.start()
    }

    fun resetView() {
        resetTransform()
        sliderPosition = 0.5f
        invalidate()
    }
}
