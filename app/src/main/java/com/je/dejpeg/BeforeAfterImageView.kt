package com.je.dejpeg

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class BeforeAfterImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Images
    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null
    
    // Drawing objects
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
        alpha = 120 // semi-transparent dark outline
    }
    private val sliderHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255) // semi-transparent white
        style = Paint.Style.FILL
        // Soft shadow for depth
        setShadowLayer(18f, 0f, 2f, Color.argb(90, 0, 0, 0))
    }
    // Inner highlight for skeuomorphic effect
    private val sliderHandleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 120, 120, 120) // semi-transparent light gray
        style = Paint.Style.FILL
    }
    // Outline for visibility in any background
    private val sliderHandleOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 40, 40, 40)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    // Arrow paint (black fill)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    // Arrow outline paint (semi-transparent white)
    private val arrowOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    
    // Slider position (0.0 to 1.0)
    private var sliderPosition = 0.5f
    private var isDraggingSlider = false
    private val sliderTouchRadius = 80f // Increased for easier touch
    private val sliderHandleRadius = 36f // Increased handle size
    private var showSlider = true
    
    // Transform matrix for zoom and pan
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    
    // Scale and translation values
    private var scaleFactor = 1.0f
    private var minScale = 1.0f
    private var maxScale = 5.0f
    private var translateX = 0f
    private var translateY = 0f
    
    // Touch handling
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    
    // Gesture detectors
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    // Image bounds
    private val imageBounds = RectF()
    private val viewBounds = RectF()
    
    // Vibration manager
    private var vibrationManager: VibrationManager? = null
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
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

        // Calculate scale to fit image in view
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        minScale = min(scaleX, scaleY)

        // Center the image
        val scaledWidth = imageWidth * minScale
        val scaledHeight = imageHeight * minScale

        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2

        imageBounds.set(0f, 0f, imageWidth, imageHeight)
        viewBounds.set(left, top, left + scaledWidth, top + scaledHeight)

        // Reset transformation
        resetTransform()
    }

    private fun resetTransform() {
        scaleFactor = minScale
        // Center the image in the view
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

        // Calculate inverse matrix for touch coordinates
        matrix.invert(inverseMatrix)

        // Constrain translation to keep image on screen
        constrainTranslation()

        // Re-apply translation after constraining
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

        // Calculate bounds to keep image visible
        val minTransX = min(0f, viewWidth - scaledWidth)
        val maxTransX = max(0f, viewWidth - scaledWidth)
        val minTransY = min(0f, viewHeight - scaledHeight)
        val maxTransY = max(0f, viewHeight - scaledHeight)

        translateX = translateX.coerceIn(minTransX, maxTransX)
        translateY = translateY.coerceIn(minTransY, maxTransY)
    }
    
    // Helper to check if only before image is available
    fun hasOnlyBeforeImage(): Boolean {
        return beforeBitmap != null && afterBitmap == null
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageBounds()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (beforeBitmap == null) return

        // Apply transformation matrix only to images
        canvas.save()
        canvas.concat(matrix)

        // Always draw the before image in full when there's no after image
        if (!showSlider || afterBitmap == null) {
            beforeBitmap?.let {
                canvas.drawBitmap(it, 0f, 0f, paint)
            }
        } else {
            // Draw after image (right side)
            afterBitmap?.let {
                canvas.save()
                canvas.clipRect(it.width * sliderPosition, 0f, it.width.toFloat(), it.height.toFloat())
                canvas.drawBitmap(it, 0f, 0f, paint)
                canvas.restore()
            }

            // Draw before image (left side)
            beforeBitmap?.let {
                canvas.save()
                canvas.clipRect(0f, 0f, it.width * sliderPosition, it.height.toFloat())
                canvas.drawBitmap(it, 0f, 0f, paint)
                canvas.restore()
            }
        }

        canvas.restore()

        // Only draw the slider if both images are available and showSlider is true
        if (showSlider && afterBitmap != null) {
            drawSlider(canvas)
        }
    }
    
    private fun drawSlider(canvas: Canvas) {
        val bitmap = beforeBitmap ?: afterBitmap ?: return

        // Calculate slider position in view coordinates without matrix
        val points = floatArrayOf(bitmap.width * sliderPosition, 0f, bitmap.width * sliderPosition, bitmap.height.toFloat())
        matrix.mapPoints(points)

        // Do NOT constrain slider within view bounds; allow it to move outside
        val sliderX = points[0]
        val topY = points[1]
        val bottomY = points[3]

        // Draw dark outline for slider line (behind)
        canvas.drawLine(sliderX, topY, sliderX, bottomY, sliderLineOutlinePaint)
        // Draw slider line (white, on top)
        canvas.drawLine(sliderX, topY, sliderX, bottomY, sliderPaint)

        // Draw slider handle (bigger, semi-transparent)
        val handleY = (topY + bottomY) / 2
        canvas.drawCircle(sliderX, handleY, sliderHandleRadius, sliderHandlePaint)

        // Draw arrows on handle (centered on new radius)
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
        // Draw outlines first
        canvas.drawPath(leftArrowPath, arrowOutlinePaint)
        canvas.drawPath(rightArrowPath, arrowOutlinePaint)
        // Draw black arrows on top
        canvas.drawPath(leftArrowPath, arrowPaint)
        canvas.drawPath(rightArrowPath, arrowPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle scale gestures first
        scaleGestureDetector.onTouchEvent(event)
        
        // Don't handle other gestures while scaling
        if (scaleGestureDetector.isInProgress) {
            return true
        }
        
        // Handle other gestures
        gestureDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
                
                // Check if touching the slider
                if (isTouchingSlider(event.x, event.y)) {
                    isDraggingSlider = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    
                    if (isDraggingSlider) {
                        // Update slider position
                        updateSliderPosition(x)
                    } else if (scaleFactor > minScale) {
                        // Pan the image
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
        // If slider is hidden, no touch events should be handled
        if (!showSlider) return false
        
        val bitmap = beforeBitmap ?: afterBitmap ?: return false

        // Calculate slider line X position in view coordinates
        val points = floatArrayOf(bitmap.width * sliderPosition, 0f, bitmap.width * sliderPosition, bitmap.height.toFloat())
        matrix.mapPoints(points)

        val sliderX = points[0]
        val topY = points[1]
        val bottomY = points[3]

        // Allow touch anywhere along the slider line (with some vertical margin)
        val verticalMargin = 60f
        val withinX = kotlin.math.abs(x - sliderX) <= sliderTouchRadius
        val withinY = y in (topY - verticalMargin)..(bottomY + verticalMargin)
        if (withinX && withinY) {
            vibrationManager?.vibrateSliderChange()
        }
        return withinX && withinY
    }
    
    private fun updateSliderPosition(x: Float) {
        // Don't update slider position if slider is hidden
        if (!showSlider) return
        
        val bitmap = beforeBitmap ?: afterBitmap ?: return
        
        // Convert touch position to image coordinates
        val points = floatArrayOf(x, 0f)
        inverseMatrix.mapPoints(points)
        
        // Calculate new slider position
        sliderPosition = (points[0] / bitmap.width).coerceIn(0f, 1f)
        vibrationManager?.vibrateSliderChange()
        invalidate()
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = scaleFactor
            val newScaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val scaleChange = newScaleFactor / prevScale

            // Focus point in view coordinates
            val focusX = detector.focusX
            val focusY = detector.focusY

            // Adjust translation so zoom is centered on gesture focal point
            translateX = (translateX - focusX) * scaleChange + focusX
            translateY = (translateY - focusY) * scaleChange + focusY

            scaleFactor = newScaleFactor

            updateMatrix()
            return true
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Toggle between min and max zoom
            val targetScale = if (scaleFactor > minScale) minScale else maxScale
            val scaleChange = targetScale / scaleFactor

            // Use double tap location as focal point
            val focusX = e.x
            val focusY = e.y

            // Adjust translation so zoom is centered on double tap location
            translateX = (translateX - focusX) * scaleChange + focusX
            translateY = (translateY - focusY) * scaleChange + focusY

            scaleFactor = targetScale

            updateMatrix()
            return true
        }
    }

    fun resetView() {
        resetTransform()
        sliderPosition = 0.5f
        invalidate()
    }
}