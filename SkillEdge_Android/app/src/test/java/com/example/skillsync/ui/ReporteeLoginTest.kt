package com.example.skillsync.ui

import com.example.skillsync.ui.auth.sanitiseWorkId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The initials-only work-ID field must accept whatever a user actually types. */
class ReporteeLoginTest {

    @Test fun stripsDomainWhenPasted() {
        assertEquals("aishwar.c", sanitiseWorkId("aishwar.c@koenig-solutions.com"))
    }

    @Test fun lowercasesAndTrims() {
        assertEquals("aishwar.c", sanitiseWorkId("  Aishwar.C "))
    }

    @Test fun dropsInternalWhitespace() {
        assertEquals("asha.k", sanitiseWorkId("asha. k"))
    }

    @Test fun plainInitialsPassThrough() {
        assertEquals("asha.k", sanitiseWorkId("asha.k"))
    }
}
