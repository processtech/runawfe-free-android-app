package ru.runa.wfe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.savedstate.SavedState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.notification.NotificationHelpers
import ru.runa.wfe.notification.NotificationHelpers.NotificationType
import ru.runa.wfe.notification.NotificationScheduler
import ru.runa.wfe.rest.TokenManager
import ru.runa.wfe.rest.ApiClient
import ru.runa.wfe.rest.ServerCheckResult

class MainActivity : AppCompatActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var firstRun: Boolean = false
    private lateinit var rootView: View

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            permissionCallback?.invoke(isGranted)
            permissionCallback = null
        }
    private var intentCallback: ((Int) -> Unit)? = null
    private val intentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> intentCallback?.invoke(result.resultCode)}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rootView = findViewById(android.R.id.content)
        // Are there no files in /data/data/{applicationId}? Then it's the very first app launch
        firstRun = this.filesDir.listFiles()?.isEmpty() ?: false
        preferencesManager = PreferencesManager.getInstance(this)
        setNavigation()
    }

    private fun setNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as NavHostFragment
        val navController = navHostFragment.navController

        // Conditional start screen
        lifecycleScope.launch {
            val wfURL = preferencesManager
                .getValue(PreferencesManager.WEBVIEW_URL, "")

            navController.popBackStack() // Don't return to start fragment
            if (wfURL.isEmpty()) {
                navController.navigate(R.id.emptyUrlDialogFragment)
            } else {
                val checkServerUrlResult = ApiClient.checkServer(wfURL)
                if (checkServerUrlResult is ServerCheckResult.Valid) {
                    ApiClient.setServerUrl(checkServerUrlResult)
                    val tokenLoadSuccess = TokenManager.loadToken(preferencesManager)
                    if (tokenLoadSuccess) {
                        navController.navigate(R.id.mainFragment)
                    } else {
                        navController.navigate(R.id.loginFragment)
                        preferencesManager.deleteKeyValue(PreferencesManager.TOKEN)
                    }
                } else {
                    Snackbar.make(rootView, R.string.invalid_url, Snackbar.LENGTH_SHORT).show()
                    navController.navigate(R.id.settingsFragment)
                }
            }

            navController.addOnDestinationChangedListener(
                object : NavController.OnDestinationChangedListener {
                    override fun onDestinationChanged(
                        controller: NavController,
                        destination: NavDestination,
                        arguments: SavedState?
                    ) {
                        if (destination.id == R.id.mainFragment) {
                            val sdkTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            if (firstRun && sdkTiramisu) {
                                requestPermission(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    R.string.permission_notification_need
                                ) { isGranted ->
                                    if (isGranted) startNotifying()
                                }
                            } else {
                                startNotifying()
                            }
                            controller.removeOnDestinationChangedListener(this)
                        }
                    }
                }
            )
        }
    }

    private fun canStartNotification(): Boolean {
        val channelsEnabled = NotificationHelpers.isChannelEnabled(NotificationType.TASK, this) ||
                NotificationHelpers.isChannelEnabled(NotificationType.MESSAGE, this)
        val permissionGranted = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
                this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!(ApiClient.isApiClientInitialized() && channelsEnabled && permissionGranted)) {
            return false
        }
        if (preferencesManager.getValue( PreferencesManager.POLLING_INTERVAL, 0) <= 0) {
            return false
        }
        return true
    }

    fun startNotifying() {
        if (canStartNotification()) {
            NotificationScheduler.start(this)
        }
    }

    fun requestPermission(permission: String, explanation: Int?, callback: ((Boolean) -> Unit)?) {
        // Create custom dialog with explanations
        val explainDialogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.permission_request_title)
            .setMessage(explanation ?: R.string.permission_need)
            .setNeutralButton(R.string.refuse_action, null)
        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (firstRun || ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                // Explain and ask permission
                callback.let { permissionCallback = it }
                explainDialogBuilder
                    .setPositiveButton(R.string.permission_set) { _, _ ->
                        requestPermissionLauncher.launch(permission)
                    }
                    .create().show()
            } else {
                /*
                * If the user denied the permission, the system dialog won't appear
                * Direct user to the app's settings
                * */
                intentCallback = {
                    // Because Android Settings app returns no informative result, re-check the permission state and send to the callback
                    callback?.invoke(
                        ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val intent = when (permission) {
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, this.packageName)
                    }

                    else -> {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", this.packageName, null))
                    }
                }
                explainDialogBuilder
                    .setPositiveButton(R.string.permission_set) { _, _ ->
                        intentLauncher.launch(intent)
                    }
                    .create().show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationScheduler.stop(this)
    }
}