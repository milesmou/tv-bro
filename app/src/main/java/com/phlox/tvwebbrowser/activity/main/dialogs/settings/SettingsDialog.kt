package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.content.Context
import com.phlox.tvwebbrowser.activity.main.dialogs.FocusRestoringDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.view.Gravity
import android.widget.LinearLayout
import android.view.WindowManager
import com.fedir.segmentedbutton.SegmentedButton
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.activity.main.SettingsModel
import com.phlox.tvwebbrowser.widgets.SegmentedButtonTabsAdapter

class SettingsDialog(context: Context, val model: SettingsModel) :
    FocusRestoringDialog(context, R.style.SettingsDialog),
    DialogInterface.OnDismissListener, VersionSettingsView.Callback {
    private var mainView: MainSettingsView? = null
    private var sbTabs: SegmentedButton

    init {
        setTitle(R.string.settings)
        setContentView(R.layout.dialog_settings)

        sbTabs = findViewById(R.id.sbTabs)
        val tabContentAdapter = object : SegmentedButtonTabsAdapter(sbTabs, findViewById(R.id.flTabsContent)) {
            override fun createContentViewForSegmentButtonId(id: Int): View {
                return when (id) {
                    R.id.btnMainTab -> {
                        mainView = MainSettingsView(context)
                        mainView!!
                    }
                    R.id.btnShortcutsTab -> ShortcutsSettingsView(context)
                    else -> {
                        val view = VersionSettingsView(context)
                        view.callback = this@SettingsDialog
                        view
                    }
                }
            }
        }

        val tabs = listOf<View>(findViewById(R.id.btnMainTab),
            findViewById(R.id.btnShortcutsTab), findViewById(R.id.btnVersionTab))
        tabs.forEachIndexed { index, tab ->
            tab.isFocusableInTouchMode = true
            tab.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus && sbTabs.checkedId != view.id) {
                    view.performClick()
                    view.requestFocus()
                }
            }
            tab.setOnKeyListener { _, keyCode, event ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.action == KeyEvent.ACTION_UP) {
                            val step = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                            tabs[(index + step).coerceIn(0, tabs.lastIndex)].requestFocus()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (event.action == KeyEvent.ACTION_UP) {
                            when (tab.id) {
                                R.id.btnMainTab -> mainView?.focusFirstOption()
                                R.id.btnVersionTab -> findViewById<View>(R.id.tvLink)?.requestFocus()
                                else -> tabContentAdapter.currentContentView?.requestFocus()
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    else -> false
                }
            }
        }

        setOnDismissListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT)
        val panelWidth = (context.resources.displayMetrics.widthPixels * 0.60f).toInt()
        for (panel in listOf<View>(sbTabs, findViewById(R.id.flTabsContent))) {
            panel.layoutParams = (panel.layoutParams as LinearLayout.LayoutParams).apply {
                width = panelWidth
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        mainView?.save()
    }

    override fun onStart() {
        super.onStart()
        val firstTab = findViewById<View>(R.id.btnMainTab)
        firstTab.requestFocus()
        firstTab.post {
            if (isShowing) firstTab.requestFocus()
        }
    }

    override fun onNeedToCloseSettings() {
        dismiss()
    }
}
