package com.devtraining.ticketsapp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AppTextField
import com.devtraining.ticketsapp.ui.components.LinkButton
import com.devtraining.ticketsapp.ui.components.LoadingButton
import com.devtraining.ticketsapp.ui.components.PasswordField
import com.devtraining.ticketsapp.ui.components.ResultMessage

@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    onComponentGalleryClick: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loading = state.login is UiState.Loading

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Event Tickets", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
                onDone = viewModel::onLoginClick,
            )
            (state.login as? UiState.Error)?.let {
                ResultMessage(it.message, isError = true)
            }
            LoadingButton("Log in", onClick = viewModel::onLoginClick, enabled = state.canSubmit, loading = loading)
            LinkButton(
                "New here? Create an account",
                onClick = onRegisterClick,
                enabled = !loading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            LinkButton(
                "View UI components",
                onClick = onComponentGalleryClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
