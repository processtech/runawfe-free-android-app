package ru.runa.wfe.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.runa.wfe.R
import ru.runa.wfe.rest.TokenManager
import ru.runa.wfe.restapi.model.WfeCredentials

data class LoginResult(
    val success: Boolean = false,
    val error: Int? = null
)

class LoginViewModel : ViewModel() {
    private var _loginForm = MutableStateFlow(LoginResult())

    fun login(login: String, password: String, onComplete: (LoginResult) -> Unit) {
        viewModelScope.launch {
            if (loginValidator(login) && passwordValidator(password)) {
                _loginForm.value = TokenManager.requestToken(WfeCredentials(login, password))
            } else {
                _loginForm.value = LoginResult(false, R.string.empty_form)
            }
            onComplete(_loginForm.value)
        }
    }

    private fun loginValidator(login: String): Boolean {
        return login.isNotBlank() && login.isNotEmpty()
    }

    private fun passwordValidator(password: String): Boolean {
        return password.isNotBlank() && password.isNotEmpty()
    }

}