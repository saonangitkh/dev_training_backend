package com.devtraining.ticketsapp.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AppTextField
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.LoadingButton
import com.devtraining.ticketsapp.ui.components.PasswordField
import com.devtraining.ticketsapp.ui.components.ResultMessage

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading = state.register is UiState.Loading

    Scaffold(topBar = { AppTopBar("Create account", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = "Full name",
                enabled = !loading,
            )
            AppTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                enabled = !loading,
                keyboardType = KeyboardType.Email,
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                enabled = !loading,
                supportingText = "At least ${RegisterViewModel.MIN_PASSWORD} characters",
                onDone = viewModel::onRegisterClick,
            )
            (state.register as? UiState.Error)?.let {
                ResultMessage(it.message, isError = true)
            }
            LoadingButton("Create account", onClick = viewModel::onRegisterClick, enabled = state.canSubmit, loading = loading)
        }
    }
}
