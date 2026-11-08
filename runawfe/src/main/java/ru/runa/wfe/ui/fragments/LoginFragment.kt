package ru.runa.wfe.ui.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.rest.TokenManager
import ru.runa.wfe.ui.login.LoginViewModel

class LoginFragment : Fragment(R.layout.login_fragment) {
    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var preferencesManager: PreferencesManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferencesManager = PreferencesManager.getInstance(view.context)
        val login: EditText = view.findViewById(R.id.login)
        val password: EditText = view.findViewById(R.id.password)
        val error: TextView = view.findViewById(R.id.error_message)
        val loginButton: Button = view.findViewById(R.id.login_button)
        val settingsButton: ImageButton = view.findViewById(R.id.settingsButton)

        loginButton.setOnClickListener {
            lifecycleScope.launch {
                if (preferencesManager
                        .getValue(PreferencesManager.WEBVIEW_URL, "").isEmpty()
                ) {
                    findNavController().navigate(R.id.emptyUrlDialogFragment)
                } else {
                    loginHandler(login, password, error)
                }
            }
        }

        settingsButton.setOnClickListener {
            findNavController().navigate(R.id.login_to_settings)
        }
    }

    private fun loginHandler(
        login: EditText,
        password: EditText,
        error: TextView
    ) {
        val loginValue = login.text.toString().trim()
        val passwordValue = password.text.toString().trim()
        loginViewModel.login(loginValue, passwordValue) { loginResult ->
            if (loginResult.success) {
                saveToken()
                val credentialsBundle = Bundle()
                credentialsBundle.putString("login", loginValue)
                credentialsBundle.putString("password", passwordValue)
                // Currently, login screen always appears after settings screen (which this line pops from the backstack)
                findNavController().navigate(
                    R.id.mainFragment,
                    credentialsBundle,
                    NavOptions.Builder().setPopUpTo(R.id.settingsFragment, inclusive = true).build()
                )
            } else {
                if (loginResult.error != null) {
                    error.text = getString(loginResult.error)
                } else {
                    error.text = getString(R.string.auth_error)
                }
            }
        }
    }

    private fun saveToken() {
        requireActivity().lifecycleScope.launch(Dispatchers.IO) {
            preferencesManager.setSecureKey(
                PreferencesManager.TOKEN,
                TokenManager.token
            )
        }
    }
}