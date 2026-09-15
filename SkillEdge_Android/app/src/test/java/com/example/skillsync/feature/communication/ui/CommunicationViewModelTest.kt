package com.example.skillsync.feature.communication.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommunicationViewModelTest {

    @Test
    fun buildRequestNeverCarriesAUserMessageKey() {
        val vm = CommunicationViewModel()
        vm.setRecipientName("Priya Rao")
        vm.setRecipientType("REPORTEE")
        vm.setPurpose("GENERAL_PROFESSIONAL")
        vm.setManagerInstruction("please be firm about the deadline")

        val request = vm.buildRequest("manager@koenig-solutions.com")

        assertFalse("no userMessage key may ever be present", request.containsKey("userMessage"))
        assertFalse("no user_message key may ever be present", request.containsKey("user_message"))
        assertEquals("please be firm about the deadline", request["myMessage"])
    }

    @Test
    fun buildRequestOmitsManagerInstructionWhenBlank() {
        val vm = CommunicationViewModel()
        val request = vm.buildRequest("manager@koenig-solutions.com")
        assertEquals("", request["myMessage"])
        assertFalse(request.containsKey("userMessage"))
    }
}
