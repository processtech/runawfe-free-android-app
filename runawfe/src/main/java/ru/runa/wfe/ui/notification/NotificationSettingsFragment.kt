package ru.runa.wfe.ui.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.runa.wfe.MainActivity
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.data.PreferencesViewModel
import ru.runa.wfe.notification.NotificationHelpers
import ru.runa.wfe.notification.NotificationHelpers.NotificationType
import ru.runa.wfe.ui.fragments.DurationPreferenceDialogFragmentCompat

class NotificationSettingsFragment : PreferenceFragmentCompat() {
    private lateinit var preferencesViewModel: PreferencesViewModel
    private val notificationPreferences = NotificationType.entries.associateBy { it.preferenceName }

    private fun pollingIntervalSummary(number: Int): String {
        return "$number ${resources.getQuantityString(R.plurals.minutes, number)}"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preferencesManager = PreferencesManager.getInstance(requireContext())
        preferencesViewModel = ViewModelProvider(
            requireActivity(),
            PreferencesViewModel.Factory(preferencesManager)
        )[PreferencesViewModel::class.java]

        // Set custom preference and it's summary
        val pollingInterval: DurationPreference? = findPreference("pollingInterval")
        var pollingIntervalValue: Long? = null
        viewLifecycleOwner.lifecycleScope.launch {
            val pollingIntervalSeconds =
                preferencesViewModel.pollingInterval.filterNotNull().first()
            pollingIntervalValue = pollingIntervalSeconds
            pollingInterval?.duration = pollingIntervalSeconds
            pollingInterval?.summary = pollingIntervalSummary(pollingIntervalSeconds.toInt() / 60)
        }

        pollingInterval?.setOnPreferenceChangeListener { _, newValue ->
            val newInterval = newValue.toString().toLongOrNull()
            newInterval?.let {
                if (newInterval != pollingIntervalValue) {
                    pollingIntervalValue = newInterval
                    preferencesViewModel.updatePreference(
                        PreferencesManager.POLLING_INTERVAL,
                        newInterval
                    )
                    pollingInterval.summary =
                        pollingIntervalSummary(newInterval.toInt() / 60)
                }
            }
            true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notification_settings, rootKey)
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
    private fun showPostNotificationPermissionRequest(channelType: NotificationType) {
        val activity: MainActivity? = (activity as? MainActivity)
        activity?.requestPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            R.string.permission_notification_need
        ) { isGranted ->
            if (isGranted) {
                activity.startNotifying()
                showNotificationChannelSettings(channelType)
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
                showNotificationChannelSettings(channelType)
            }
        } else if (preference.key == "disableChannels") {
            val intent = Intent()
                .setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
            context?.startActivity(intent)
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun showNotificationChannelSettings(type: NotificationType) {
        val intent = Intent()
            .setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, type.channelId)
        context?.startActivity(intent)
    }
}