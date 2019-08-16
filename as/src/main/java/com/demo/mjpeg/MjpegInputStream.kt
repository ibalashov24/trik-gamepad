// http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.demo.mjpeg

import android.util.Log

import org.apache.commons.io.input.BoundedInputStream

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.util.Arrays
import java.util.Properties

class MjpegInputStream(`in`: InputStream) : DataInputStream(BufferedInputStream(`in`, FRAME_MAX_LENGTH)) {
    private val props = Properties()


    @Throws(IOException::class)
    private fun getEndOfSequence(sequence: ByteArray): Int {
        mark(FRAME_MAX_LENGTH)
        var seqIndex = 0

        try {
            for (i in 0 until FRAME_MAX_LENGTH) {
                val c = readUnsignedByte()
                if (c < 0)
                    return -1
                if (c.toByte() == sequence[seqIndex]) {
                    seqIndex++
                    if (seqIndex == sequence.size)
                        return i + 1
                } else {
                    seqIndex = 0
                }
            }
            return -1
        } catch (e: IOException) {
            return -1
        } finally {
            reset()
        }
    }

    @Throws(IOException::class)
    private fun getStartOfSequence(sequence: ByteArray): Int {
        val end = getEndOfSequence(sequence)
        return if (end < 0) -1 else end - sequence.size
    }

    @Throws(IOException::class)
    fun readMjpegFrame(): BoundedInputStream? {
        var contentLength = -1
        val contentAttrPos = getStartOfSequence(CONTENT_LENGTH_MARKER!!)
        if (contentAttrPos < 0 || skipBytes(contentAttrPos) < contentAttrPos)
            throw IOException("JPG stream is totally broken or this is extremely huge image")

        try {
            val headerLen = getStartOfSequence(SOI_MARKER)
            val headerIn = BoundedInputStream(this, headerLen.toLong())
            headerIn.isPropagateClose = false
            props.clear()
            props.load(headerIn)
            contentLength = Integer.parseInt(props.getProperty(CONTENT_LENGTH))
            headerIn.close()

            if (contentLength >= 0 && available() < 2 * contentLength) {
                // we must be at the very beginning of data already, but ....
                val skip = getStartOfSequence(SOI_MARKER)
                if (skipBytes(skip) < skip)
                    return null
                val s = BoundedInputStream(this, contentLength.toLong())
                s.isPropagateClose = false
                return s
            }
        } catch (e: IOException) {
            //e.getStackTrace();
            Log.d(TAG, "catch exn hit", e)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "catch exn hit", e)
        }

        try {
            if (contentLength < 0) {
                Log.e(TAG, "Skipping to recover")
                contentLength = getStartOfSequence(CONTENT_LENGTH_MARKER)
            } else {
                Log.i(TAG, "Frame dropped.")
            }
            Log.v(TAG, "$contentLength bytes to skip until next frame header.")
            val skipped = skipBytes(contentLength)
            if (skipped != contentLength)
                Log.w(TAG, "Skipped only$skipped bytes instead of $contentLength")
        } catch (e: IOException) {
            val stackTrace = e.stackTrace
            Log.e(TAG, "Failed to skip bad data:" + e + "\n" + Arrays.toString(stackTrace))
        }

        return null
    }

    companion object {
        private val TAG = "MjpegInputStream"
        private val CONTENT_LENGTH = "Content-Length"
        private val HEADER_MAX_LENGTH = 10000
        private val FRAME_MAX_LENGTH = 300000 + HEADER_MAX_LENGTH
        private val SOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        //private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
        private val CONTENT_LENGTH_MARKER = getUTF8Bytes(CONTENT_LENGTH)

        private fun getUTF8Bytes(s: String): ByteArray? {
            try {
                return s.toByteArray(charset("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                return null
            }

        }
    }

}