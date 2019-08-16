// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

import org.apache.commons.io.input.BoundedInputStream

import java.io.IOException
import java.util.Locale

class MjpegView : SurfaceView, SurfaceHolder.Callback {
    private var thread: MjpegViewThread? = null
    private var mIn: MjpegInputStream? = null
    private var mRun: Boolean = false
    private var surfaceDone: Boolean = false
    private val fpsTextPaint = Paint()
    private var dispWidth: Int = 0
    private var dispHeight: Int = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    private fun init(context: Context) {
        val holder = holder
        holder.addCallback(this)
        thread = MjpegViewThread(holder)
        isFocusable = true
        fpsTextPaint.textAlign = Paint.Align.RIGHT
        fpsTextPaint.textSize = 12f
        fpsTextPaint.typeface = Typeface.DEFAULT
        fpsTextPaint.color = Color.WHITE
        dispWidth = width
        dispHeight = height
    }


    fun setSource(source: MjpegInputStream?) {
        mIn = source
    }

    @Synchronized
    fun startPlayback() {
        startPlaybackInternal()
    }

    private fun startPlaybackInternal() {
        if (mIn != null) {
            mRun = true
            thread!!.start()
        }
    }

    @Synchronized
    fun stopPlayback() {
        stopPlaybackInternal()
    }

    private fun stopPlaybackInternal() {
        if (mRun) {
            mRun = false
            thread!!.join()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
        thread!!.setSurfaceSize(w, h)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceDone = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceDone = false
        stopPlayback()
    }

    internal inner class MjpegViewThread(private val mSurfaceHolder: SurfaceHolder) {
        private var thread: Thread? = null

        private fun initThread() {
            join()
            if (thread != null)
                join()
            thread = MjpegRenderThread(this.mSurfaceHolder)
        }

        fun join() {
            if (thread != null) {
                var retry = true
                while (retry) {
                    try {
                        thread!!.join(3000, 0)
                        retry = false
                    } catch (e: InterruptedException) {
                        Log.e(this.javaClass.simpleName, Log.getStackTraceString(e))
                    }

                }
                thread = null
            }
        }

        fun setSurfaceSize(width: Int, height: Int) {
            synchronized(mSurfaceHolder) {
                dispWidth = width
                dispHeight = height
            }
        }

        fun start() {
            initThread()
            if (thread != null)
                thread!!.start()
        }

    }

    /// TODO: rewrite from scratch. needs redesign.
    private inner class MjpegRenderThread(private val mSurfaceHolder: SurfaceHolder) : Thread() {
        private var mFrameCounter: Int = 0
        private var mStart: Long = 0

        private var mBitmap: Bitmap? = null
        private var mFrame: BoundedInputStream? = null
        private val mTempStorage = ByteArray(100000)
        private val mMode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        private var mFpsStr = ""

        override fun run() {
            mStart = System.currentTimeMillis()

            while (mRun) {
                if (!surfaceDone)
                    continue
                var canvas: Canvas? = null
                try {
                    mFrame = mIn!!.readMjpegFrame()
                    if (mFrame == null)
                        continue

                    val destRect = ExtractNextBitmap() ?: continue

                    canvas = mSurfaceHolder.lockCanvas()

                    if (canvas != null && mBitmap != null)
                        DrawToCanvas(canvas, destRect)

                } catch (e: IOException) {
                    mRun = false
                } finally {

                    if (canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas)
                    }
                    if (mFrame != null) {
                        try {
                            // Stream is bound to mBitmap, but we do not need
                            // both of them later, mBitmap to be reused.
                            mFrame!!.close()
                        } catch (e: IOException) {
                            mRun = false
                        } finally {
                            mFrame = null
                        }
                    }
                }
            }
        }

        @Synchronized
        private fun ExtractNextBitmap(): Rect? {

            val opts = BitmapFactory.Options()
            //            opts.inJustDecodeBounds = true;
            //            try {
            //                mFrame.mark(mFrame.available());
            //                BitmapFactory.decodeStream(mFrame, null, opts);
            //                mFrame.reset();
            //            } catch (IOException e) {
            //                return null;
            //            }


            //            opts.inSampleSize = 1;
            //            while (destRect.width() < opts.outWidth >> 1 && destRect.height() < opts.outHeight >> 1) {
            //                opts.inSampleSize <<= 1;
            //                opts.outWidth >>= 1;
            //                opts.outHeight >>= 1;
            //            }
            //
            //            opts.inScaled = true;

            //final int scale = (int) getResources().getDisplayMetrics().density;
            //opts.inTargetDensity = scale * opts.inSampleSize;
            //opts.inScreenDensity = scale;
            //opts.inDensity = scale * opts.outHeight / destRect.height();

            opts.inBitmap = mBitmap // reuse if possible
            opts.inMutable = true

            opts.inPurgeable = true
            opts.inInputShareable = true
            //opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inTempStorage = mTempStorage

            var bm: Bitmap?

            try {
                //opts.inJustDecodeBounds = false;
                bm = BitmapFactory.decodeStream(mFrame, null, opts)
            } catch (e: IllegalArgumentException) {
                bm = null
            }

            if (bm == null)
                return null

            if (mBitmap != null && bm != mBitmap) {
                // Was not reused
                mBitmap!!.recycle()
                Log.v(TAG, "Bitmap was not reused, recycled.")
            }

            mBitmap = bm
            //mBitmap.setDensity(Bitmap.DENSITY_NONE);
            return destRect(bm.width, bm.height)
        }

        private fun DrawToCanvas(canvas: Canvas, destRect: Rect) {

            mFrameCounter++
            val now = System.currentTimeMillis()
            val elapsedMs = now - mStart
            val TIME_FRAME_MS = 5000
            if (elapsedMs >= TIME_FRAME_MS) {
                Log.v(TAG, "elapsed $elapsedMs")
                mStart = now
                val fps = 1000.0f * mFrameCounter / TIME_FRAME_MS
                mFrameCounter = 0
                mFpsStr = String.format(Locale.getDefault(), "%.1f", fps)
            }

            synchronized(mSurfaceHolder) {
                canvas.drawColor(Color.BLACK)
                synchronized(this) {
                    if (mBitmap != null)
                        canvas.drawBitmap(mBitmap!!, null, destRect, null)
                }
                canvas.drawText(mFpsStr, (dispWidth - 1).toFloat(), -fpsTextPaint.ascent(), fpsTextPaint)
            }


        }


        private fun destRect(bmw: Int, bmh: Int): Rect {
            var bmw = bmw
            var bmh = bmh
            val tempX: Int
            val tempY: Int
            val bmasp = bmw.toFloat() / bmh.toFloat()
            bmw = dispWidth
            bmh = (dispWidth / bmasp).toInt()
            if (bmh > dispHeight) {
                bmh = dispHeight
                bmw = (dispHeight * bmasp).toInt()
            }
            tempX = dispWidth / 2 - bmw / 2
            tempY = dispHeight / 2 - bmh / 2
            return Rect(tempX, tempY, bmw + tempX, bmh + tempY)
        }

    }

    companion object {
        private val TAG = "MjpegView"
    }


}