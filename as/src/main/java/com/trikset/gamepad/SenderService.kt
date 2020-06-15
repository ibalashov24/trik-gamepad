package com.trikset.gamepad

import android.os.AsyncTask
import android.util.Log
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

class SenderService {

    internal interface IShowTextCallback {
        fun show(text: String?)
    }

    internal var keepaliveTimeout = DEFAULT_KEEPALIVE
    private val mKeepAliveTimer = KeepAliveTimer()
    private val mSyncFlag = Any()
    internal var showTextCallback: OnEventListener<String>? = null
        set
    private var mOut: PrintWriter? = null
    internal var onDisconnectedListener: OnEventListener<String>? = null
        set
    var hostAddr: String? = null
        private set
    private var mHostPort = 0

    @Volatile
    private var mConnectTask: AsyncTask<Void?, Void?, PrintWriter?>? = null
    private fun connectAsync(): AsyncTask<Void?, Void?, PrintWriter?>? {
        synchronized(mSyncFlag) {
            if (mConnectTask != null) return null
            mConnectTask = PrintWriterAsyncTask()
            return (mConnectTask as PrintWriterAsyncTask).execute()
        }
    }

    // socket is closed from PrintWriter.close()
    private fun connectToTRIK(): PrintWriter? {
        synchronized(mSyncFlag) {
            try {
                Log.e("TCP Client", "C: Connecting...")
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddr, mHostPort), TIMEOUT)
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.setSoLinger(true, 0)
                socket.trafficClass = 0x0F // high priority, no-delay
                socket.oobInline = true
                socket.shutdownInput()
                val osw =  //Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                        //new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8):
                        OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                mKeepAliveTimer.restartKeepAliveTimer()

                // currently does nothing
                // socket.setPerformancePreferences(connectionTime, latency,
                // bandwidth);
                try {
                    return PrintWriter(osw, true)
                } catch (e: Exception) {
                    Log.e("TCP", "GetStream: Error", e)
                    socket.close()
                    osw.close()
                }
            } catch (e: IOException) {
                Log.e("TCP", "Connect: Error", e)
            }
            // mLastConnectionAttemptTimestamp = currentTime;
            return null
        }
    }

    fun disconnect(reason: String) {
        mKeepAliveTimer.stopKeepAliveTimer()
        if (mOut != null) {
            mOut!!.close()
            mOut = null
            Log.d("TCP", "Disconnected.")
            val l = onDisconnectedListener
            l?.onEvent(reason)
        }
    }

    fun send(command: String) {
        if (mOut == null) {
            connectAsync() // is synchronized on the same object as SendCommandAsyncTask
        }
        Log.d("TCP", "Sending '$command'")
        SendCommandAsyncTask(command).execute()
        mKeepAliveTimer.restartKeepAliveTimer()
    }

    fun setTarget(hostAddr: String, hostPort: Int) {
        if (!hostAddr.equals(this.hostAddr, ignoreCase = true) || mHostPort != hostPort) {
            disconnect("Target changed.")
        }
        this.hostAddr = hostAddr
        mHostPort = hostPort
    }

    fun setKeepaliveTimeout(timeout: Int) {
        if (timeout != keepaliveTimeout) {
            mKeepAliveTimer.restartKeepAliveTimer()
            keepaliveTimeout = timeout
        }
    }

    fun getKeepaliveTimeout(): Int {
        return keepaliveTimeout
    }

    internal interface OnEventListener<ArgType> {
        fun onEvent(arg: ArgType)
    }

    private inner class PrintWriterAsyncTask : AsyncTask<Void?, Void?, PrintWriter?>() {
        override fun doInBackground(vararg p0: Void?): PrintWriter? {
            return connectToTRIK()
        }

        override fun onPostExecute(result: PrintWriter?) {
            mOut = result
            val cb = showTextCallback
            cb?.onEvent("Connection to " + hostAddr + ':' + mHostPort
                    + if (mOut != null) " established." else " error.")
            mConnectTask = null
        }
    }

    private inner class SendCommandAsyncTask internal constructor(private val command: String) : AsyncTask<Void?, Void?, Void?>() {
        override fun doInBackground(vararg p0: Void?): Void? {
            synchronized(mSyncFlag) {
                // TODO: reimplement with Handle instead of multiple chaotic
                // AsyncTasks
                if (mOut != null) mOut!!.println(command)
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            if (mOut == null || mOut!!.checkError()) {
                Log.e("TCP", "NotSent: $command")
                disconnect("Send failed.")
            }
        }

    }

    private inner class KeepAliveTimer : Timer() {
        private var task = KeepAliveTimerTask()
        fun restartKeepAliveTimer() {
            stopKeepAliveTimer()
            task = KeepAliveTimerTask()
            // Using '300' in order to compensate ping
            val realTimeout = keepaliveTimeout - 300
            scheduleAtFixedRate(task, realTimeout.toLong(), realTimeout.toLong())
        }

        fun stopKeepAliveTimer() {
            task.cancel()
            purge()
        }

        private inner class KeepAliveTimerTask : TimerTask() {
            override fun run() {
                if (mOut != null) {
                    val command = "keepalive " + Integer.toString(keepaliveTimeout)
                    Log.d("TCP", String.format("Sending %s message", command))
                    SendCommandAsyncTask(command).execute()
                } else {
                    stopKeepAliveTimer()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_KEEPALIVE = 5000
        const val MINIMAL_KEEPALIVE = 1000
        private const val TIMEOUT = 5000
    }
}