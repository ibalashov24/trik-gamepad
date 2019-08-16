package com.trikset.gamepad

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import java.util.Locale
import java.util.Objects
import java.util.Optional

import android.content.Context.CLIPBOARD_SERVICE

class SettingsFragment : PreferenceFragmentCompat() {

    private fun initializeAboutSystemField() {
        val myActivity = activity ?: return

        val aboutSystem = findPreference(SK_ABOUT_SYSTEM)
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
        aboutSystem.summary = getString(R.string.tap_to_copy) + ":" + systemInfo

        // Copying system info to the clipboard on click
        val listener = Preference.OnPreferenceClickListener {
            val clipboard = myActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.about_system), systemInfo)
            clipboard.primaryClip = clip

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
            val preference = findPreference(preferenceKey)
            preference.summary = preference.sharedPreferences.getString(preferenceKey, "")
            preference.onPreferenceChangeListener = listener
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_general, rootKey)

        initializeAboutSystemField()
        initializeDynamicPreferenceSummary()
    }

    companion object {
        val SK_HOST_ADDRESS = "hostAddress"
        val SK_HOST_PORT = "hostPort"
        val SK_SHOW_PADS = "showPads"
        val SK_VIDEO_URI = "videoURI"
        val SK_WHEEL_STEP = "wheelSens"
        val SK_ABOUT_SYSTEM = "aboutSystem"
        val SK_KEEPALIVE = "keepaliveTimeout"
    }
}
