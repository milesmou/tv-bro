package com.phlox.tvwebbrowser.activity.main.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View

/** Returns remote navigation to the invoking control after a modal closes. */
open class FocusRestoringDialog(context: Context, theme: Int) : Dialog(context, theme) {
    private val activity = context as? Activity
    private var previousFocus: View? = null

    override fun onStart() {
        previousFocus = activity?.currentFocus
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        val target = previousFocus ?: return
        target.post {
            if (activity?.isFinishing != true && !isShowing && target.isAttachedToWindow && target.isShown) {
                target.requestFocus()
            }
        }
        previousFocus = null
    }
}
