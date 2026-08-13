package ru.runa.wfe.ui.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.launch
import ru.runa.wfe.MainActivity
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.notification.NotificationHelpers
import ru.runa.wfe.notification.NotificationHelpers.NotificationType
import ru.runa.wfe.ui.fragments.DurationPreferenceDialogFragmentCompat

class NotificationSettingsFragment : PreferenceFragmentCompat() {
    private lateinit var preferencesManager: PreferencesManager
    private val notificationPreferences = NotificationType.entries.associateBy { it.preferenceName }

    private fun pollingIntervalSummary(number: Int): String {
        return "$number ${resources.getQuantityString(R.plurals.minutes, number)}"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notification_settings, rootKey)
        preferencesManager = PreferencesManager.getInstance(requireContext())

        val pollingIntervalSeconds = preferencesManager.getValue(
            PreferencesManager.POLLING_INTERVAL,
            DurationPreference.DEFAULT
        )

        // Set custom preference and it's summary
        val pollingInterval: DurationPreference? = findPreference("pollingInterval")
        pollingInterval?.summary = pollingIntervalSummary(pollingIntervalSeconds.toInt() / 60)

        lifecycleScope.launch {
            if (!preferencesManager.hasKey(PreferencesManager.POLLING_INTERVAL)) {
                preferencesManager.setKey(
                    PreferencesManager.POLLING_INTERVAL,
                    DurationPreference.DEFAULT
                )
            }
        }

        pollingInterval?.setOnPreferenceChangeListener { _, newValue ->
            val newInterval = newValue.toString().toLongOrNull()
            val currentPollingInterval = preferencesManager.getValue(
                PreferencesManager.POLLING_INTERVAL,
                DurationPreference.DEFAULT
            )
            newInterval?.let {
                if (newInterval != currentPollingInterval) {
                    lifecycleScope.launch {
                        preferencesManager.setKey(
                            PreferencesManager.POLLING_INTERVAL,
                            newInterval
                        )
                    }
                    pollingInterval.summary = pollingIntervalSummary((newInterval / 60).toInt())
                }
            }
            true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is DurationPreference) {
            val dialogFragment: DialogFragment =
                DurationPreferenceDialogFragmentCompat.newInstance(preference.key)
            // Currently (March of 2026) setTargetFragment must be called despite the deprecation
            @Suppress("DEPRECATION")
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, null)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showPostNotificationPermissionRequest(type: NotificationType) {
        val activity: MainActivity? = (activity as? MainActivity)
        activity?.requestPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            R.string.permission_notification_need
        ) { isGranted ->
            if (isGranted) {
                activity.startNotifying()
                showNotificationChannelSettingsOreo(type)
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val channelType: NotificationType? = notificationPreferences[preference.key]
        if (channelType != null) {
            // If the channel wasn't created, investigate why
            if (!NotificationHelpers.channelExists(channelType, requireContext())) {
                // On Android API 33+ (Android 13) the channel cannot be created without permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    showPostNotificationPermissionRequest(channelType)
                } else {
                    /*
                    * The app has the permission for API 33+
                    * For all API versions, the problem is in notification requirements, explain it
                    * Check ru.runa.wfe.MainActivity.canStartNotification
                    * */
                    val explainDialogBuilder = AlertDialog.Builder(requireContext())
                        .setTitle(R.string.channel_not_exist)
                        .setMessage(R.string.permission_notification_need)
                        .setNeutralButton("OK", null)
                    explainDialogBuilder.create().show()
                }
            } else {
                showNotificationChannelSettingsOreo(channelType)
            }
        } else if (preference.key == "disableChannels") {
            val intent = Intent()
                .setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
            context?.startActivity(intent)
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun showNotificationChannelSettingsOreo(type: NotificationType) {
        val intent = Intent()
            .setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, type.channelId)
        context?.startActivity(intent)
    }
}