package emu.skyline.applet.swkbd

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import emu.skyline.databinding.KeyboardDialogBinding
import java.util.concurrent.FutureTask


class SoftwareKeyboardDialog : DialogFragment() {
    private var _binding : KeyboardDialogBinding? = null
    private val binding get() = _binding!!
    private var futureResult : FutureTask<String> = FutureTask<String> { return@FutureTask binding.TextInput.text.toString() }


    override fun onCreateView(inflater : LayoutInflater, container : ViewGroup?, savedInstanceState : Bundle?) = KeyboardDialogBinding.inflate(inflater).also {
        it.OkButton.setOnClickListener { futureResult.run() }
        it.cancelButton.setOnClickListener { dismiss() }
        _binding = it
    }.root

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun waitForButton() : String {
        val result = futureResult.get()
        futureResult = FutureTask<String> { return@FutureTask binding.TextInput.text.toString() }

        return result
    }
}