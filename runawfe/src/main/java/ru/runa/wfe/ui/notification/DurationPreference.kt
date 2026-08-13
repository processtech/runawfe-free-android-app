package ru.runa.wfe.ui.notification

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import ru.runa.wfe.R

class DurationPreference(
    context: Context,
    attrs: AttributeSet?
) : DialogPreference(context, attrs) {
    var duration: Long = DEFAULT
        get() = field
        set(value) {
            field = value
            persistLong(value)
        }

    override fun getDialogLayoutResource(): Int {
        return R.layout.duration_picker_dialog
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        duration = getPersistedLong(defaultValue as? Long ?: DEFAULT)
    }

    companion object {
        const val DEFAULT = 10 * 60L // 10 min default value
    }
}