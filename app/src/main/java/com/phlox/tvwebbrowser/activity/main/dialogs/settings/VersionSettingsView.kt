package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.activity.IncognitoModeMainActivity
import com.phlox.tvwebbrowser.activity.main.MainActivity
import com.phlox.tvwebbrowser.databinding.ViewSettingsVersionBinding
import com.phlox.tvwebbrowser.utils.activity
import com.phlox.tvwebbrowser.webengine.WebEngineFactory

class VersionSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    private val vb = ViewSettingsVersionBinding.inflate(LayoutInflater.from(context), this, true)
    var callback: Callback? = null

    interface Callback {
        fun onNeedToCloseSettings()
    }

    init {
        vb.tvVersion.text = context.getString(R.string.version_s, BuildConfig.VERSION_NAME)
        val engine = AppContext.provideConfig().webEngine
        val engineVersion = if (engine == Config.ENGINE_WEB_VIEW) {
            WebViewCompat.getCurrentWebViewPackage(context)?.versionName
                ?: WebEngineFactory.getWebEngineVersionString()
        } else WebEngineFactory.getWebEngineVersionString()
        vb.tvWebViewVersion.text = context.getString(R.string.engine_version_s, "$engine · $engineVersion")
        vb.tvLink.setOnClickListener { loadUrl("https://github.com/truefedex/tv-bro") }
        vb.tvLicense.setOnClickListener {
            loadUrl("https://raw.githubusercontent.com/truefedex/tv-bro/refs/heads/master/LICENSE.md")
        }
        vb.tvPrivacy.setOnClickListener {
            loadUrl("https://raw.githubusercontent.com/truefedex/tv-bro/refs/heads/master/PRIVACY.md")
        }
    }

    private fun loadUrl(url: String) {
        callback?.onNeedToCloseSettings()
        val activityClass = if (AppContext.provideConfig().incognitoMode)
            IncognitoModeMainActivity::class.java else MainActivity::class.java
        activity?.startActivity(Intent(activity, activityClass).apply { data = url.toUri() })
    }
}
