package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {
    private fun initializeAboutSystemField() {
        val myActivity = activity ?: return
        val aboutSystem = findPreference<Preference>(SK_ABOUT_SYSTEM)
        val displayMetrics = DisplayMetrics()
        myActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val systemInfo = String.format(
                Locale.ENGLISH,
                "Android %s; SDK %d; Resolution %dx%d; PPI %dx%d",
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                displayMetrics.heightPixels,
                displayMetrics.widthPixels,
                displayMetrics.ydpi.toInt(),
                displayMetrics.xdpi.toInt())
        aboutSystem!!.summary = getString(R.string.tap_to_copy) + ":" + systemInfo

        // Copying system info to the clipboard on click
        val listener = Preference.OnPreferenceClickListener {
            val clipboard = myActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.about_system), systemInfo)
            clipboard.setPrimaryClip(clip)
            val copiedToClipboardToast = Toast.makeText(
                    myActivity.applicationContext,
                    getString(R.string.copied_to_clipboard),
                    Toast.LENGTH_SHORT)
            copiedToClipboardToast.show()
            true
        }
        aboutSystem.onPreferenceClickListener = listener
    }

    private fun initializeDynamicPreferenceSummary() {
        val listener = Preference.OnPreferenceChangeListener { preference, value ->
            preference.summary = value.toString()
            true
        }
        for (preferenceKey in arrayOf(SK_HOST_ADDRESS, SK_HOST_PORT, SK_KEEPALIVE)) {
            val preference = findPreference<Preference>(preferenceKey)
            preference!!.summary = preference.sharedPreferences.getString(preferenceKey, "")
            preference.onPreferenceChangeListener = listener
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)
        initializeAboutSystemField()
        initializeDynamicPreferenceSummary()
    }

    companion object {
        const val SK_HOST_ADDRESS = "hostAddress"
        const val SK_HOST_PORT = "hostPort"
        const val SK_SHOW_PADS = "showPads"
        const val SK_VIDEO_URI = "videoURI"
        const val SK_WHEEL_STEP = "wheelSens"
        const val SK_ABOUT_SYSTEM = "aboutSystem"
        const val SK_KEEPALIVE = "keepaliveTimeout"
    }
}