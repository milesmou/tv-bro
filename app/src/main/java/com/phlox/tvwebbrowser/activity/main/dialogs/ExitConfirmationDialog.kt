package com.phlox.tvwebbrowser.activity.main.dialogs

import android.content.Context
import android.view.WindowManager
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.DialogExitConfirmationBinding

class ExitConfirmationDialog(context: Context, onExit: () -> Unit) :
    FocusRestoringDialog(context, R.style.BookmarksDialog) {
    private val binding = DialogExitConfirmationBinding.inflate(layoutInflater)

    init {
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        binding.tvMessage.text = context.getString(R.string.exit_confirmation, context.getString(R.string.app_name_short))
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnExit.setOnClickListener {
            dismiss()
            onExit()
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = context.resources.displayMetrics
        window?.setLayout(minOf((420 * metrics.density).toInt(), (metrics.widthPixels * 0.9f).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT)
        binding.btnCancel.requestFocus()
    }
}
