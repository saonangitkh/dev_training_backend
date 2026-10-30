package com.devtraining.ticketsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devtraining.ticketsapp.ui.gallery.ComponentGalleryScreen
import com.devtraining.ticketsapp.ui.login.LoginScreen
import com.devtraining.ticketsapp.ui.navigation.AppNavHost
import com.devtraining.ticketsapp.ui.register.RegisterScreen
import com.devtraining.ticketsapp.ui.theme.TicketsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = (application as TicketsApp).container.tokenStore
        setContent {
            TicketsTheme {
                // Login, logout and a 401 all flip this flag, so the right screen is always shown.
                val loggedIn by tokenStore.loggedIn.collectAsStateWithLifecycle()
                val isAdmin by tokenStore.isAdmin.collectAsStateWithLifecycle()
                // The component gallery is reachable from the login screen, so it works without the backend.
                var showGallery by rememberSaveable { mutableStateOf(false) }
                var showRegister by rememberSaveable { mutableStateOf(false) }
                // Registering logs in; after logging out, start again from the login screen.
                LaunchedEffect(loggedIn) {
                    if (loggedIn) showRegister = false
                }
                when {
                    showGallery -> {
                        BackHandler { showGallery = false }
                        ComponentGalleryScreen(onBack = { showGallery = false })
                    }
                    loggedIn -> AppNavHost(isAdmin = isAdmin)
                    showRegister -> {
                        BackHandler { showRegister = false }
                        RegisterScreen(onBack = { showRegister = false })
                    }
                    else -> LoginScreen(
                        onRegisterClick = { showRegister = true },
                        onComponentGalleryClick = { showGallery = true },
                    )
                }
            }
        }
    }
}
