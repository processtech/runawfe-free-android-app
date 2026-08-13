package ru.runa.wfe

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController

class EmptyURLDialogFragment : DialogFragment() {
    var activityOfMessage: Activity? = null

    @Deprecated("Deprecated in Java")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Введите адрес")
            builder.setMessage("Адресная строка пустая")
                .setPositiveButton("ОК") {
                        dialog, id ->
                    findNavController().navigate(R.id.to_settings)
                    dialog.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

}