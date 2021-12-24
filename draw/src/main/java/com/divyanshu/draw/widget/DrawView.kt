package com.divyanshu.draw.widget

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.divyanshu.draw.listener.BitmapDragAndDropListener
import com.divyanshu.draw.model.BitmapModel
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class DrawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mTouchMode: Int = 0

    private var mPaths = LinkedHashMap<MyPath, PaintOptions>()

    private var mLastPaths = LinkedHashMap<MyPath, PaintOptions>()
    private var mUndonePaths = LinkedHashMap<MyPath, PaintOptions>()
    private var mBitmapArrayList: ArrayList<BitmapModel> = ArrayList()

    private var mPaint = Paint()
    private var mPath = MyPath()
    private var mPaintOptions = PaintOptions()

    private var mCurX = 0f
    private var mCurY = 0f
    private var mStartX = 0f
    private var mStartY = 0f
    private var mIsSaving = false
    private var mIsStrokeWidthBarEnabled = false

    private var mImageList = LinkedList<Bitmap?>()
    private var mImageDraw = false
    private var mImageBitmap: Bitmap? = null
    private var mImageMove = false

    private var mImageRegion = Region()

    private var configuration: ViewConfiguration
    private var mTouchSlop: Int

    private var mScreenWidth: Int
    private var mScreenHeight: Int
    private var mSelectIndex: Int = -1

    var isEraserOn = false
        private set

    init {
        mPaint.apply {
            color = mPaintOptions.color
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = mPaintOptions.strokeWidth
            isAntiAlias = true
            configuration = ViewConfiguration.get(context)
            mTouchSlop = configuration.scaledTouchSlop

            val display = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            mScreenWidth = display.defaultDisplay.width
            mScreenHeight = display.defaultDisplay.height
        }
    }

    /**
     * 뒤로가기
     * */
    fun undo() {
        if (mPaths.isEmpty() && mLastPaths.isNotEmpty()) {
            mPaths = mLastPaths.clone() as LinkedHashMap<MyPath, PaintOptions>
            mLastPaths.clear()
            invalidate()
            return
        }
        if (mPaths.isEmpty()) {
            return
        }
        val lastPath = mPaths.values.lastOrNull()
        val lastKey = mPaths.keys.lastOrNull()

        mPaths.remove(lastKey)
        if (lastPath != null && lastKey != null) {
            mUndonePaths[lastKey] = lastPath
        }
        invalidate()
    }

    /**
     * 되돌리기
     * */
    fun redo() {
        if (mUndonePaths.keys.isEmpty()) {
            return
        }

        val lastKey = mUndonePaths.keys.last()
        addPath(lastKey, mUndonePaths.values.last())
        mUndonePaths.remove(lastKey)
        invalidate()
    }

    /**
     * 펜 색 설정
     * */
    fun setColor(newColor: Int) {
        @ColorInt
        val alphaColor = ColorUtils.setAlphaComponent(newColor, mPaintOptions.alpha)
        mPaintOptions.color = alphaColor
        if (mIsStrokeWidthBarEnabled) {
            invalidate()
        }
    }

    /**
     * 투명도
     * */
    fun setAlpha(newAlpha: Int) {
        val alpha = (newAlpha*255)/100
        mPaintOptions.alpha = alpha
        setColor(mPaintOptions.color)
    }

    /**
    * 펜 굵기
    * */
    fun setStrokeWidth(newStrokeWidth: Float) {
        mPaintOptions.strokeWidth = newStrokeWidth
        if (mIsStrokeWidthBarEnabled) {
            invalidate()
        }
    }

    /**
    * Bitmap 이미지 가져오기
    * */
    fun getImageFile(uri: Uri) {
        val ims = context.contentResolver.openInputStream(uri)
        mImageDraw = true
        mImageBitmap = BitmapFactory.decodeStream(ims).copy(Bitmap.Config.ARGB_8888, true)

        val left = (mScreenWidth / 2) - (mImageBitmap?.width!! / 2)
        val top = (mScreenHeight / 2) - (mImageBitmap?.height!! / 2)

        val imagerect = Rect()
        imagerect.set(left, top, left + mImageBitmap?.width!!, top + mImageBitmap?.height!!)

        mBitmapArrayList.add(BitmapModel(mImageBitmap!!, imagerect, mImageRegion))

        mImageList.add(mImageBitmap)
        invalidate()
    }

    /**
    * 비트맵 저장
    * */
    fun saveBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        mIsSaving = true
        draw(canvas)
        mIsSaving = false
        return bitmap
    }

    /**
    * 획 수 추가
    * */
    private fun addPath(path: MyPath, options: PaintOptions) {
        mPaths[path] = options
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for ((key, value) in mPaths) {
            changePaint(value)
            canvas.drawPath(key, mPaint)
        }

        changePaint(mPaintOptions)
        canvas.drawPath(mPath, mPaint)

        if(mImageDraw) {
            if(mImageBitmap != null) {
                for(bitmap in mBitmapArrayList) {
                    canvas.drawBitmap(bitmap.bitmap, null, bitmap.rect, null)
                }
            }
        }
    }

    /**
    * 색, 굵기 변경
    * */
    private fun changePaint(paintOptions: PaintOptions) {
        mPaint.color = if (paintOptions.isEraserOn) Color.WHITE else paintOptions.color
        mPaint.strokeWidth = paintOptions.strokeWidth
    }
    
    /**
    *  캔버스 클리어
    * */
    fun clearCanvas() {
        mLastPaths = mPaths.clone() as LinkedHashMap<MyPath, PaintOptions>
        mPath.reset()
        mPaths.clear()
        invalidate()
    }

    /**
    * 펜 터치
    * */
    private fun actionDown(x: Float, y: Float) {
        mPath.reset()
        mPath.moveTo(x, y)
        mCurX = x
        mCurY = y
    }
    
    /**
     * 펜 터치 이후 움직임
     * */
    private fun actionMove(x: Float, y: Float) {
        mPath.quadTo(mCurX, mCurY, (x + mCurX) / 2, (y + mCurY) / 2)
        mCurX = x
        mCurY = y
    }

    /**
     * 펜 터치 이후 때기
     * */
    private fun actionUp() {
        mPath.lineTo(mCurX, mCurY)

        // draw a dot on click
        if (mStartX == mCurX && mStartY == mCurY) {
            mPath.lineTo(mCurX, mCurY + 2)
            mPath.lineTo(mCurX + 1, mCurY + 2)
            mPath.lineTo(mCurX + 1, mCurY)
        }

        mPaths[mPath] = mPaintOptions
        mPath = MyPath()
        mPaintOptions = PaintOptions(mPaintOptions.color, mPaintOptions.strokeWidth, mPaintOptions.alpha, mPaintOptions.isEraserOn)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mStartX = x
                mStartY = y
                mImageMove = getBitmapIndex(Point(x.toInt(), y.toInt())) > -1
                if(!mImageMove) {
                    actionDown(x, y)
                    mUndonePaths.clear()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if(mImageMove) {
                    lBitmapDragAndDropListener.onDragging(x.toInt(), y.toInt())
                } else {
                    actionMove(x, y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if(!mImageMove) {
                    actionUp()
                }
                mSelectIndex = -1
                mImageMove = false
            }
        }
        invalidate()
        return true
    }

    /**
     * 비트맵 드래그 앤 드랍
    * */
    private val lBitmapDragAndDropListener = object: BitmapDragAndDropListener {
        override fun onDragging(x: Int, y: Int) {
            val point = Point(x, y)
            val distX = abs(x - mStartX.toInt())
            val distY = abs(y - mStartY.toInt())
            if(distX > mTouchSlop || distY > mTouchSlop) {
                if(mSelectIndex > -1) {
                    moveBitmap(mBitmapArrayList[mSelectIndex], point)
                }
            }
        }
    }

    /**
     * 비트맵 이동
     * */
    private fun moveBitmap(bitmap: BitmapModel, point: Point) {
        val deltaX = point.x - mStartX.toInt()
        val deltaY = point.y - mStartY.toInt()
        val selectBitmap = bitmap.bitmap
        val selectRect = bitmap.rect
        val selectRegion = bitmap.region
        if((selectRect.left + deltaX) > 0 && (selectRect.right + deltaX) < mScreenWidth && (selectRect.top + deltaY) > 0 && (selectRect.bottom + deltaY) < mScreenHeight) {
            selectRect.left = selectRect.left + deltaX
            selectRect.top = selectRect.top + deltaY
            selectRect.right = selectRect.left + selectBitmap.width
            selectRect.bottom = selectRect.top + selectBitmap.height
            selectRegion.set(selectRect)
            mStartX = point.x.toFloat()
            mStartY = point.y.toFloat()
            invalidate()
        }
    }

    /**
    * 어떤 비트맵이 선택되었는지 확인
    * */
    private fun getBitmapIndex(point: Point): Int {
        var index = -1
        for(indices in mBitmapArrayList.indices) {
            if(mBitmapArrayList[indices].rect.contains(point.x, point.y)) {
                index =  indices
                mSelectIndex = indices
            }
        }
        return index
    }

    /**
    * 지우개 이동
    * */
    fun toggleEraser() {
        isEraserOn = !isEraserOn
        mPaintOptions.isEraserOn = isEraserOn
        invalidate()
    }
}
