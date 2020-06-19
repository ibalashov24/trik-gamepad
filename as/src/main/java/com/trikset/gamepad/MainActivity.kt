package com.trikset.gamepad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuItemCompat
import com.demo.mjpeg.MjpegView
import com.trikset.gamepad.MainActivity
import com.trikset.gamepad.SenderService.OnEventListener
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {
    var hideRunnable: HideRunnable? = null
    private var mRestartCallback: Runnable? = null
    private var mSensorManager: SensorManager? = null
    private var mAngle = 0 // -100% = 0

    // ...
    // +100%
    private var mWheelEnabled = false
    var senderService: SenderService? = null
    private var mWheelStep = 7
    private var mVideo: MjpegView? = null
    private var mVideoURL: URL? = null
    private var mSharedPreferencesListener: OnSharedPreferenceChangeListener? = null

    // @SuppressWarnings("deprecation")
    // @TargetApi(16)
    private fun createPad(id: Int, strId: String) {
        val pad = findViewById<SquareTouchPadLayout>(id)
        if (pad != null) {
            pad.padName = "pad $strId"
            pad.setSender(senderService)
        }
        // if (android.os.Build.VERSION.SDK_INT >= 16) {
        // pad.setBackground(image);
        // } else {
        // pad.setBackgroundDrawable(image);
        // }
    }

    override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {
        // TODO Auto-generated method stub
    }

    protected fun setDisplayParameters() {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        run {
            val a = supportActionBar
            if (a != null) {
                a.setDisplayShowHomeEnabled(true)
                a.setDisplayUseLogoEnabled(false)
                a.setLogo(R.drawable.trik_gamepad_logo_512x512)
                a.setDisplayShowTitleEnabled(true)
            }
        }
        senderService = SenderService()
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mVideo = findViewById(R.id.video)
        recreateMagicButtons(5)
        run {
            senderService!!.onDisconnectedListener = object : OnEventListener<String> {
                override fun onEvent(arg: String) {
                    toast("Disconnected.$arg")
                }
            }
            senderService!!.showTextCallback = object : OnEventListener<String> {
                override fun onEvent(arg: String) {
                    toast(arg)
                }
            }
        }
        run {
            val btnSettings = findViewById<Button>(R.id.btnSettings)
            btnSettings?.setOnClickListener {
                val a = supportActionBar
                if (a != null) setSystemUiVisibility(!a.isShowing)
            }
        }
        run {
            val controlsOverlay = findViewById<View>(R.id.controlsOverlay)
            controlsOverlay?.bringToFront()
        }
        run {
            createPad(R.id.leftPad, "1")
            createPad(R.id.rightPad, "2")
        }
        run {
            val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
            mSharedPreferencesListener = object : OnSharedPreferenceChangeListener {
                private var mPrevAlpha = 0f
                override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
                    val addr = sharedPreferences.getString(SettingsFragment.SK_HOST_ADDRESS, "192.168.77.1")
                    var portNumber = 4444
                    val portStr = sharedPreferences.getString(SettingsFragment.SK_HOST_PORT, "4444")
                    try {
                        portNumber = portStr!!.toInt()
                    } catch (e: NumberFormatException) {
                        toast("Port number '$portStr' is incorrect.")
                    }
                    val oldAddr = senderService!!.hostAddr
                    senderService!!.setTarget(addr!!, portNumber)
                    run {
                        val a = supportActionBar
                        if (a != null) a.setTitle(addr) else toast("Can not change title, not a problem")
                    }
                    if (!addr.equals(oldAddr, ignoreCase = true)) {

                        // update video stream URI when target addr changed
                        sharedPreferences.edit()
                                .putString(SettingsFragment.SK_VIDEO_URI, "http://" + addr.trim { it <= ' ' }
                                        + ":8080/?action=stream")
                                .apply()
                    }
                    run {
                        val defAlpha = 100
                        var padsAlpha = defAlpha
                        try {
                            padsAlpha = sharedPreferences.getString(SettingsFragment.SK_SHOW_PADS, defAlpha.toString())!!.toInt()
                        } catch (nfe: NumberFormatException) {
                            // unchanged
                        }
                        val alpha = Math.max(0, Math.min(255, padsAlpha)) / 255.0f
                        val alphaUp = AlphaAnimation(mPrevAlpha, alpha)
                        mPrevAlpha = alpha
                        alphaUp.fillAfter = true
                        alphaUp.duration = 2000
                        val co = findViewById<View>(R.id.controlsOverlay)
                        co?.startAnimation(alphaUp)
                        val btns = findViewById<View>(R.id.buttons)
                        btns?.startAnimation(alphaUp)
                    }
                    run {

                        // "http://trackfield.webcam.oregonstate.edu/axis-cgi/mjpg/video.cgi?resolution=320x240";
                        val videoStreamURI = sharedPreferences.getString(SettingsFragment.SK_VIDEO_URI, "http://"
                                + addr + ":8080/?action=stream")

                        // --no-sout-audio --sout
                        // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                        // works only with vcodec=mp4v without audio :(

                        // http://developer.android.com/reference/android/media/MediaPlayer.html
                        // http://developer.android.com/guide/appendix/media-formats.html
                        mVideoURL = try {
                            if ("" == videoStreamURI) null else URI(
                                    videoStreamURI).toURL()
                        } catch (e: URISyntaxException) {
                            toast("Illegal video stream URL")
                            Log.e(TAG, "onSharedPreferenceChanged: ", e)
                            null
                        } catch (e: MalformedURLException) {
                            toast("Illegal video stream URL")
                            Log.e(TAG, "onSharedPreferenceChanged: ", e)
                            null
                        }
                    }
                    run {
                        mWheelStep = Integer
                                .getInteger(
                                        sharedPreferences.getString(SettingsFragment.SK_WHEEL_STEP, mWheelStep.toString()), mWheelStep)
                        mWheelStep = Math.max(1, Math.min(100, mWheelStep))
                    }
                    run {
                        try {
                            val timeout = sharedPreferences.getString(
                                    SettingsFragment.SK_KEEPALIVE,
                                    Integer.toString(SenderService.DEFAULT_KEEPALIVE))!!.toInt()
                            if (timeout < SenderService.MINIMAL_KEEPALIVE) {
                                toast(String.format(
                                        Locale.US,
                                        getString(R.string.keepalive_must_be_not_less),
                                        SenderService.MINIMAL_KEEPALIVE))
                                sharedPreferences
                                        .edit()
                                        .putString(
                                                SettingsFragment.SK_KEEPALIVE,
                                                Integer.toString(senderService!!.keepaliveTimeout))
                                        .apply()
                            } else {
                                senderService!!.keepaliveTimeout = timeout
                            }
                        } catch (e: NumberFormatException) {
                            toast(getString(R.string.keepalive_must_be_positive_decimal))
                            sharedPreferences
                                    .edit()
                                    .putString(
                                            SettingsFragment.SK_KEEPALIVE,
                                            Integer.toString(senderService!!.keepaliveTimeout))
                                    .apply()
                        }
                    }
                }
            }
            (mSharedPreferencesListener as OnSharedPreferenceChangeListener).onSharedPreferenceChanged(prefs, SettingsFragment.SK_HOST_ADDRESS)
            prefs.registerOnSharedPreferenceChangeListener(mSharedPreferencesListener)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)

        // TODO: remove this hack
        val w = MenuItemCompat.getActionView(menu.findItem(R.id.wheel)) as CheckBox
        w.text = resources.getString(R.string.menu_wheel)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val settings = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(settings)
                true
            }
            R.id.wheel -> {
                mWheelEnabled = !mWheelEnabled
                item.isChecked = mWheelEnabled
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        mSensorManager!!.unregisterListener(this)
        senderService!!.disconnect("Inactive gamepad")
        if (mVideo != null) {
            mVideo!!.stopPlayback()
            if (mRestartCallback != null) {
                mVideo!!.removeCallbacks(mRestartCallback)
                mRestartCallback = null
            }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (mVideo != null) {
            if (mRestartCallback != null) {
                mVideo!!.removeCallbacks(mRestartCallback)
            }
            mRestartCallback = Runnable {
                StartReadMjpegAsync(mVideo!!).execute(mVideoURL)
                if (mRestartCallback != null && mVideo != null) // drop HTTP connection and restart
                    mVideo!!.postDelayed(mRestartCallback, 30000)
            }
            mVideo!!.post(mRestartCallback)
        }
        mSensorManager!!.registerListener(this, mSensorManager!!.getDefaultSensor(Sensor.TYPE_ALL),
                SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (!mWheelEnabled) return
            processSensor(event.values)
        } else {
            Log.i("Sensor", event.sensor.type.toString())
        }
    }

    private fun processSensor(values: FloatArray) {
        val WHEEL_BOOSTER_MULTIPLIER = 1.5
        val x = values[0].toDouble()
        val y = values[1].toDouble()
        if (x < 1e-6) return
        var angle = (200 * WHEEL_BOOSTER_MULTIPLIER * Math.atan2(y, x) / Math.PI).toInt()
        if (Math.abs(angle) < 10) {
            angle = 0
        } else if (angle > 100) {
            angle = 100
        } else if (angle < -100) {
            angle = -100
        }
        if (Math.abs(mAngle - angle) < mWheelStep) return
        mAngle = angle
        senderService!!.send("wheel $mAngle")
    }

    private fun recreateMagicButtons(count: Int) {
        val buttonsView = findViewById<ViewGroup>(R.id.buttons) ?: return
        buttonsView.removeAllViews()
        for (num in 1..count) {
            val btn = Button(this@MainActivity)
            btn.isHapticFeedbackEnabled = true
            btn.gravity = Gravity.CENTER
            btn.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val name = num.toString()
            btn.text = name
            btn.setBackgroundResource(R.drawable.button_shape)
            btn.setOnClickListener {
                val sender = senderService
                if (sender != null) {
                    sender.send("btn $name down") // TODO: "up" via
                    // TouchListner
                    btn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                }
            }
            buttonsView.addView(btn)
        }
    }

    @SuppressLint("NewApi")
    private fun setSystemUiVisibility(show: Boolean) {
        var flags = 0
        val mainView = findViewById<View>(R.id.main) ?: return
        val sdk = Build.VERSION.SDK_INT
        if (sdk >= Build.VERSION_CODES.KITKAT) {
            flags = flags or if (show) 0 else View.SYSTEM_UI_FLAG_IMMERSIVE
            //Not using View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY; we use handcrafted autohide
        }
        if (sdk >= Build.VERSION_CODES.JELLY_BEAN) {
            flags = flags or (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            flags = flags or if (show) 0 else View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        if (sdk >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            flags = flags or if (show) View.SYSTEM_UI_FLAG_VISIBLE else View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LOW_PROFILE
            mainView.systemUiVisibility = flags
        } else {
            val a = supportActionBar
            if (a != null) {
                if (show) a.show() else a.hide()
            }
        }
        val r = hideRunnable
        if (r != null) {
            mainView.removeCallbacks(r)
            mainView.postDelayed(r, 3000)
        }
    }

    private fun toast(text: String) {
        runOnUiThread { Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show() }
    }

    inner class HideRunnable : Runnable {
        override fun run() {
            setSystemUiVisibility(false)
        }
    }

    override fun onDestroy() {
        mSensorManager!!.unregisterListener(this)
        if (mVideo != null) {
            if (mRestartCallback != null) mVideo!!.removeCallbacks(mRestartCallback)
            mRestartCallback = null
            mVideo!!.stopPlayback()
            mVideo = null
        }
        val mainView = findViewById<View>(R.id.main)
        mainView.removeCallbacks(hideRunnable)
        val buttonsView = findViewById<ViewGroup>(R.id.buttons)
        if (buttonsView != null) {
            for (i in 0 until buttonsView.childCount) {
                buttonsView.getChildAt(i).setOnClickListener(null)
            }
        }
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        btnSettings?.setOnClickListener(null)
        val pad1 = findViewById<SquareTouchPadLayout>(R.id.leftPad)
        pad1?.setSender(null)
        val pad2 = findViewById<SquareTouchPadLayout>(R.id.rightPad)
        pad2?.setSender(null)
        PreferenceManager.getDefaultSharedPreferences(baseContext)
                .unregisterOnSharedPreferenceChangeListener(mSharedPreferencesListener)
        senderService!!.onDisconnectedListener = null
        senderService!!.showTextCallback = null
        senderService = null
        hideRunnable = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}