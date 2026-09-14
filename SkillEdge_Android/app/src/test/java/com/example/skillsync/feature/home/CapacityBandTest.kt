package com.example.skillsync.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `capacity_bucket` has two independent backend producers with different
 * vocabularies (backend.py ~2776 and ~12939). This pins every canonical
 * value from both to its band, and — the actual regression this guards
 * against — confirms an unrecognized value maps to UNKNOWN rather than
 * silently masquerading as a known, positive-looking state.
 */
class CapacityBandTest {

    @Test
    fun dashboardPathValues_mapToTheirBands() {
        assertEquals(CapacityBand.ON_BENCH, CapacityBand.from("On Bench"))
        assertEquals(CapacityBand.LIGHT, CapacityBand.from("Light"))
        assertEquals(CapacityBand.BALANCED, CapacityBand.from("Balanced"))
        assertEquals(CapacityBand.STRETCHED, CapacityBand.from("Stretched"))
        assertEquals(CapacityBand.UNKNOWN, CapacityBand.from("Unknown"))
    }

    @Test
    fun reporteeSnapshotPathValues_mapToTheirBands() {
        assertEquals(CapacityBand.STRETCHED, CapacityBand.from("Stretched"))
        assertEquals(CapacityBand.DELIVERING, CapacityBand.from("Delivering"))
        assertEquals(CapacityBand.ON_BENCH, CapacityBand.from("On Bench"))
        assertEquals(CapacityBand.STEADY, CapacityBand.from("Steady"))
    }

    @Test
    fun caseAndWhitespaceAreTolerated() {
        assertEquals(CapacityBand.STRETCHED, CapacityBand.from("  stretched "))
        assertEquals(CapacityBand.ON_BENCH, CapacityBand.from("ON BENCH"))
    }

    @Test
    fun unrecognizedOrMissingValue_mapsToUnknown_neverToAKnownState() {
        assertEquals(CapacityBand.UNKNOWN, CapacityBand.from(null))
        assertEquals(CapacityBand.UNKNOWN, CapacityBand.from(""))
        assertEquals(CapacityBand.UNKNOWN, CapacityBand.from("Overbooked"))
        assertEquals(CapacityBand.UNKNOWN, CapacityBand.from("Available"))
    }

    @Test
    fun unknownSeverity_isNeverGood() {
        val trainer = mapOf("capacity_bucket" to "Overbooked", "feedback_risk" to "")
        val severity = teamCardSeverity(
            trainer = trainer, capability = null, calendarAvailability = null, openActionCount = 0,
        )
        // The exact non-Good level isn't the point; that it must not be Good is.
        assert(severity != com.example.skillsync.theme.Severity.Good) {
            "An unrecognized capacity_bucket must never read as Good/healthy"
        }
    }
}
