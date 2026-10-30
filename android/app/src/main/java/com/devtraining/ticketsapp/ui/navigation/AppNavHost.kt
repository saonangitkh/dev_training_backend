package com.devtraining.ticketsapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devtraining.ticketsapp.ui.bookings.MyBookingsScreen
import com.devtraining.ticketsapp.ui.eventdetail.EventDetailScreen
import com.devtraining.ticketsapp.ui.eventdetail.EventDetailViewModel
import com.devtraining.ticketsapp.ui.eventform.EventFormScreen
import com.devtraining.ticketsapp.ui.eventform.EventFormViewModel
import com.devtraining.ticketsapp.ui.events.EventListScreen

private object Routes {
    const val EVENTS = "events"
    const val EVENT_DETAIL = "events/{${EventDetailViewModel.ARG_EVENT_ID}}"
    const val MY_BOOKINGS = "bookings"
    const val NEW_EVENT = "new-event"
    const val EDIT_EVENT = "events/{${EventFormViewModel.ARG_EVENT_ID}}/edit"

    fun eventDetail(eventId: Long) = "events/$eventId"
    fun editEvent(eventId: Long) = "events/$eventId/edit"
}

/**
 * Screens shown while logged in. The login and register screens are handled by MainActivity.
 * [isAdmin] only shows or hides the admin controls; the server checks the role on every request.
 */
@Composable
fun AppNavHost(isAdmin: Boolean) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Routes.EVENTS) {
        composable(Routes.EVENTS) {
            EventListScreen(
                isAdmin = isAdmin,
                onEventClick = { navController.navigate(Routes.eventDetail(it)) },
                onMyBookingsClick = { navController.navigate(Routes.MY_BOOKINGS) },
                onNewEventClick = { navController.navigate(Routes.NEW_EVENT) },
            )
        }
        composable(
            Routes.EVENT_DETAIL,
            arguments = listOf(navArgument(EventDetailViewModel.ARG_EVENT_ID) { type = NavType.LongType }),
        ) { entry ->
            val eventId = entry.arguments?.getLong(EventDetailViewModel.ARG_EVENT_ID) ?: return@composable
            EventDetailScreen(
                isAdmin = isAdmin,
                onBack = navController::popBackStack,
                onMyBookingsClick = { navController.navigate(Routes.MY_BOOKINGS) },
                onEditClick = { navController.navigate(Routes.editEvent(eventId)) },
            )
        }
        composable(Routes.NEW_EVENT) {
            EventFormScreen(
                onBack = navController::popBackStack,
                // Show the new event; Back from there returns to the list.
                onSaved = { event ->
                    navController.navigate(Routes.eventDetail(event.id)) { popUpTo(Routes.EVENTS) }
                },
            )
        }
        composable(
            Routes.EDIT_EVENT,
            arguments = listOf(navArgument(EventFormViewModel.ARG_EVENT_ID) { type = NavType.LongType }),
        ) {
            // The detail screen reloads the event from the server when it is shown again.
            EventFormScreen(onBack = navController::popBackStack, onSaved = { navController.popBackStack() })
        }
        composable(Routes.MY_BOOKINGS) {
            MyBookingsScreen(isAdmin = isAdmin, onBack = navController::popBackStack)
        }
    }
}
