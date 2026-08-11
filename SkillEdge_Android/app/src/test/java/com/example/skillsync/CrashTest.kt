package com.example.skillsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Production guard on the manager-intelligence endpoint.
 *
 * This test used to fetch `/api/data/unified-manager-intelligence` with no
 * credentials and assert the payload parsed — which is to say it asserted the
 * exact PII leak that audit Task 1 closed (commit `e2214da`). Once the route
 * moved behind `_v2_manager_session`, the old test failed for the right reason,
 * so it is inverted here: an unauthenticated read must be refused.
 *
 * The call is a real network round-trip, so an unreachable host skips the test
 * rather than failing it — Render cold starts must not break an APK build.
 */
class CrashTest {

    @Test
    fun unifiedManagerIntelligence_refusesUnauthenticatedReads() {
        val url = URL(
            "https://skilledge-backend-fpcl.onrender.com" +
                "/api/data/unified-manager-intelligence?email=aishwar_v@koenig-solutions.com"
        )
        val status = try {
            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                responseCode.also { disconnect() }
            }
        } catch (e: Exception) {
            assumeNoException("Backend unreachable — skipping the live auth probe", e)
            return
        }
        // The security property is that an unauthenticated caller never receives
        // data — not that the service is up. A 5xx means Render is cold-starting
        // or mid-deploy, which is a reason to skip, not to fail a build: this
        // test broke the v3.10.0 gate on a 503 while the endpoint was in fact
        // correctly returning 401 seconds later.
        assumeTrue("Backend unavailable ($status) — skipping the live auth probe",
                   status < 500)
        assertNotEquals("Manager PII must never be readable without a session",
                        200, status)
        assertEquals("Unauthenticated reads must be refused with 401", 401, status)
    }
}
