// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask
package com.trikset.gamepad

import android.os.AsyncTask
import android.util.Log
import com.demo.mjpeg.MjpegInputStream
import com.demo.mjpeg.MjpegView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class StartReadMjpegAsync(private val mv: MjpegView) : AsyncTask<URL?, Void?, MjpegInputStream?>() {

    // @Nullable
    protected override fun doInBackground(vararg urls: URL): MjpegInputStream? {
        val url = urls[0]
        if (url != null) {
            try {
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                val s = MjpegInputStream(c.inputStream)
                Log.i("JPGReader", "Restarted connection.")
                return s
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }

    override fun onPostExecute(result: MjpegInputStream?) {
        mv.stopPlayback()
        mv.setSource(result)
        mv.startPlayback()
    }

}