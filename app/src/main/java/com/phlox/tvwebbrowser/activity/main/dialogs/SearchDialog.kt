package com.phlox.tvwebbrowser.activity.main.dialogs

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.DialogSearchBinding

class SearchDialog(context: Context, initialUrl: String, private val onSearch: (String) -> Unit) :
    Dialog(context, R.style.BookmarksDialog) {
    private val binding = DialogSearchBinding.inflate(layoutInflater)

    init {
        setContentView(binding.root)
        binding.etSearch.setText(initialUrl)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSearch.setOnClickListener { submit() }
        binding.etSearch.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                submit()
                true
            } else false
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.72f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding.etSearch.requestFocus()
        binding.etSearch.selectAll()
    }

    private fun submit() {
        val text = binding.etSearch.text.toString().trim()
        if (text.isEmpty()) return
        onSearch(text)
        dismiss()
    }
}
