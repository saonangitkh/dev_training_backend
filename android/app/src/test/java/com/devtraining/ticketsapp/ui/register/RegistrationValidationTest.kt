package com.devtraining.ticketsapp.ui.register

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationValidationTest {

    @Test
    fun `valid input passes`() {
        assertNull(registrationError("Alice", "alice@example.com", "Password123"))
    }

    @Test
    fun `short password is rejected before sending`() {
        assertEquals("Password must be at least 8 characters.", registrationError("Alice", "alice@example.com", "short"))
    }

    @Test
    fun `invalid email is rejected before sending`() {
        assertEquals("Enter a valid email address.", registrationError("Alice", "alice.example.com", "Password123"))
    }

    @Test
    fun `blank name is rejected before sending`() {
        assertEquals("Enter your full name.", registrationError("  ", "alice@example.com", "Password123"))
    }
}
