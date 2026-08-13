package ru.runa.wfe.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.rest.ApiClient
import ru.runa.wfe.rest.ServerCheckResult
import ru.runa.wfe.ui.notification.NotificationSettingsFragment

class SettingsFragment : Fragment(R.layout.settings_fragment) {
    private lateinit var preferencesManager: PreferencesManager

    private lateinit var showUrlCheckbox: CheckBox
    private lateinit var changeURLView: SearchView
    private lateinit var backButton: ImageButton
    private var isShowUrl: Boolean = false
    private var snackbarToLogin: Snackbar? = null
    private lateinit var previousUrl: String
    private var newServerUrlSet = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferencesManager = PreferencesManager.getInstance(view.context)
        showUrlCheckbox = view.findViewById(R.id.showUrlCheckbox)
        changeURLView = view.findViewById(R.id.searchView)
        backButton = view.findViewById(R.id.backButton)

        val wfurl = preferencesManager
            .getValue(PreferencesManager.WEBVIEW_URL, "")
        changeURLView.setQuery(wfurl, true)
        previousUrl = wfurl

        isShowUrl = preferencesManager
            .getValue(PreferencesManager.SHOW_URL, false)
        showUrlCheckbox.isChecked = isShowUrl

        view.findViewById<LinearLayout>(R.id.rootLayout).setOnClickListener {
            hideKeyboard()
        }

        backButton.setOnClickListener {
            savePreferences()
            if (!newServerUrlSet) {
                if (!findNavController().navigateUp()) {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            } else {
                findNavController().navigate(
                    R.id.settings_to_login,
                    null,
                    NavOptions.Builder().setPopUpTo(R.id.loginFragment, inclusive = false).build()
                )
            }
        }

        requireActivity()
            .onBackPressedDispatcher
            .addCallback(getViewLifecycleOwner(),
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        savePreferences()
                        isEnabled = false
                        if (!newServerUrlSet) {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        } else {
                            findNavController().navigate(
                                R.id.settings_to_login,
                                null,
                                NavOptions.Builder()
                                    .setPopUpTo(R.id.loginFragment, inclusive = false).build()
                            )
                        }
                    }
                }
            )

        showUrlCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isShowUrl = isChecked
            saveShowUrl()
        }

        changeURLView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                onUrlChanged()
            }
        }

        if (childFragmentManager.findFragmentById(R.id.notificationSettingsContainer) == null) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.notificationSettingsContainer, NotificationSettingsFragment())
                .commit()
        }
    }

    override fun onDestroyView() {
        snackbarToLogin?.dismiss()
        snackbarToLogin = null
        super.onDestroyView()
    }

    private fun loginScreenSuggest() {
        view?.let {
            snackbarToLogin = Snackbar.make(
                it,
                R.string.login_suggestion,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.button_login) {
                    findNavController().navigate(R.id.settings_to_login)
                }
            snackbarToLogin?.show()
        }
    }

    private fun saveShowUrl() {
        lifecycleScope.launch {
            preferencesManager.setKey(PreferencesManager.SHOW_URL, isShowUrl)
        }
    }

    private fun savePreferences() {
        saveShowUrl()
        onUrlChanged()
    }

    private fun onUrlChanged() {
        val changeUrl = changeURLView.query.toString()
        if (changeUrl.isEmpty()) {
            findNavController().navigate(
                R.id.emptyUrlDialogFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.settingsFragment, inclusive = true).build()
            )
            return
        }
        if (changeUrl == previousUrl) {
            return
        }
        val originChangeUrl = ApiClient.toOrigin(changeUrl)
        val areUrlHostsEqual = originChangeUrl == ApiClient.toOrigin(previousUrl)
        lifecycleScope.launch {
            if (areUrlHostsEqual) {
                preferencesManager.setKey(PreferencesManager.WEBVIEW_URL, changeUrl)
                ApiClient.setServerUrl(ServerCheckResult.Valid(originChangeUrl))
                previousUrl = changeUrl
            } else {
                val checkResult: ServerCheckResult = ApiClient.checkServer(changeUrl)
                if (checkResult is ServerCheckResult.Valid) {
                    preferencesManager.setKey(PreferencesManager.WEBVIEW_URL, changeUrl)
                    ApiClient.setServerUrl(checkResult)
                    previousUrl = changeUrl
                    newServerUrlSet = true
                    preferencesManager.deleteKeyValue(PreferencesManager.TOKEN)
                    loginScreenSuggest()
                } else {
                    view?.let {
                        Snackbar.make(
                            it,
                            if (checkResult is ServerCheckResult.Invalid)
                                R.string.invalid_url
                            else R.string.network_error_url,
                            Snackbar.LENGTH_SHORT
                        )
                            .show()
                    }
                }
            }
        }
    }

    private fun hideKeyboard() {
        val view = requireActivity().currentFocus
        if (view != null) {
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}