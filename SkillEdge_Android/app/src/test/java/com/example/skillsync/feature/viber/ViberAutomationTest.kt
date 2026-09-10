package com.example.skillsync.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.skillsync.data.cache.LocalCache
import com.example.skillsync.data.cache.ViberConfig
import com.example.skillsync.data.cache.ViberConfigStore
import com.example.skillsync.data.cache.ViberOutboxItem
import com.example.skillsync.data.cache.ViberOutboxStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ViberAutomationTest {

    private val managerEmail = "manager.test@koenig-solutions.com"

    @Before
    fun setUp() {
        LocalCache.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testViberOutboxStoreEnqueueDeduplicationAndState() {
        val item1 = ViberOutboxItem(
            id = "test_item_1",
            category = ViberOutboxItem.CAT_DEMAND,
            recipientName = "Alice Trainer",
            recipientEmail = "alice@koenig-solutions.com",
            courseName = "AZ-104: Microsoft Azure Administrator",
            messageText = "Hello _Alice_,\n\nWe have an unallocated AZ-104 batch on 2026-04-10. Please reply to accept.\n\n_Thank you._",
            status = ViberOutboxItem.STATUS_QUEUED,
        )
        val item2 = ViberOutboxItem(
            id = "test_item_2",
            category = ViberOutboxItem.CAT_WEEKLY,
            recipientName = "Bob Instructor",
            recipientEmail = "bob@koenig-solutions.com",
            courseName = "AI-102",
            messageText = "Hello _Bob_,\n\nWeekly standpoint note: you are scheduled for AI-102.\n\n_Thank you._",
            status = ViberOutboxItem.STATUS_QUEUED,
        )

        // Enqueue items
        val added = ViberOutboxStore.enqueue(managerEmail, listOf(item1, item2))
        assertEquals(2, added)

        // Enqueue duplicate item1 -> should return 0 added
        val duplicateAdded = ViberOutboxStore.enqueue(managerEmail, listOf(item1))
        assertEquals(0, duplicateAdded)

        // Pending items
        val pending = ViberOutboxStore.getPending(managerEmail)
        assertEquals(2, pending.size)

        // Mark item1 sent
        ViberOutboxStore.markStatus(managerEmail, "test_item_1", ViberOutboxItem.STATUS_SENT)
        val pendingAfterSent = ViberOutboxStore.getPending(managerEmail)
        assertEquals(1, pendingAfterSent.size)
        assertEquals("test_item_2", pendingAfterSent[0].id)

        // Mark item2 failed
        ViberOutboxStore.markStatus(managerEmail, "test_item_2", ViberOutboxItem.STATUS_FAILED, "Network timeout")
        val allItems = ViberOutboxStore.getAll(managerEmail)
        val failedItem = allItems.find { it.id == "test_item_2" }
        assertNotNull(failedItem)
        assertEquals(ViberOutboxItem.STATUS_FAILED, failedItem?.status)
        assertEquals("Network timeout", failedItem?.errorMessage)

        // Retry failed item
        ViberOutboxStore.retry(managerEmail, "test_item_2")
        val retriedPending = ViberOutboxStore.getPending(managerEmail)
        assertEquals(1, retriedPending.size)
        assertEquals("test_item_2", retriedPending[0].id)

        // Clear sent items
        ViberOutboxStore.clearSent(managerEmail)
        val afterClear = ViberOutboxStore.getAll(managerEmail)
        assertEquals(1, afterClear.size)
        assertEquals("test_item_2", afterClear[0].id)
    }

    @Test
    fun testViberConfigStorePersistence() {
        val customConfig = ViberConfig(
            autoSendDemand = true,
            autoSendWeekly = false,
            autoSendNudges = true,
            dispatchMode = ViberConfig.MODE_BOT_API,
            viberBotToken = "test-token-12345",
            webhookUrl = "https://api.test.com/viber",
            reporteePhoneMap = mapOf("alice@koenig-solutions.com" to "+919876543210"),
        )

        assertTrue(ViberConfigStore.save(managerEmail, customConfig))

        val loaded = ViberConfigStore.load(managerEmail)
        assertEquals(true, loaded.autoSendDemand)
        assertEquals(false, loaded.autoSendWeekly)
        assertEquals(ViberConfig.MODE_BOT_API, loaded.dispatchMode)
        assertEquals("test-token-12345", loaded.viberBotToken)
        assertEquals("+919876543210", loaded.reporteePhoneMap["alice@koenig-solutions.com"])

        // Update phone
        ViberConfigStore.updatePhone(managerEmail, "bob@koenig-solutions.com", "+919811122233")
        val updated = ViberConfigStore.load(managerEmail)
        assertEquals("+919811122233", updated.reporteePhoneMap["bob@koenig-solutions.com"])
        assertEquals("+919876543210", updated.reporteePhoneMap["alice@koenig-solutions.com"])
    }
}
