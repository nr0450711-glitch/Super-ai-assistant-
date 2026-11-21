package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.system.DeviceContact
import com.example.system.DeviceTelemetry
import com.example.system.DeviceTelemetryHelper
import com.example.system.InstalledAppInfo
import com.example.system.LocalCommandRouter
import com.example.system.LocalRouteOutcome
import com.example.system.PendingActionConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalCommandRouterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val telemetryHelper = DeviceTelemetryHelper(context)
    private val sampleTelemetry = DeviceTelemetry(
        batteryPercent = 82,
        isCharging = true,
        ramFreeMb = 2400L
    )

    @Test
    fun `routes Hindi telemetry query locally`() {
        val outcome = LocalCommandRouter.route(
            input = "मेरा बैटरी स्टेटस बताओ",
            telemetry = sampleTelemetry,
            telemetryHelper = telemetryHelper,
            contacts = emptyList(),
            apps = emptyList(),
            preferredLanguage = "auto"
        )
        assertTrue(outcome is LocalRouteOutcome.Handled)
        val handled = outcome as LocalRouteOutcome.Handled
        assertTrue(handled.reply.contains("82%"))
    }

    @Test
    fun `prepares confirmed call for matching contact`() {
        val contacts = listOf(DeviceContact("1", "Aarav Sharma", "+919876543210"))
        val outcome = LocalCommandRouter.route(
            input = "Call Aarav",
            telemetry = sampleTelemetry,
            telemetryHelper = telemetryHelper,
            contacts = contacts,
            apps = emptyList(),
            preferredLanguage = "en-IN"
        )
        assertTrue(outcome is LocalRouteOutcome.Handled)
        val confirmation = (outcome as LocalRouteOutcome.Handled).confirmation
        assertTrue(confirmation is PendingActionConfirmation.CallContact)
        assertEquals("+919876543210", (confirmation as PendingActionConfirmation.CallContact).phoneNumber)
    }

    @Test
    fun `parses alarm command and forwards general chat`() {
        val alarmOutcome = LocalCommandRouter.route(
            input = "Set alarm 6:30 pm",
            telemetry = sampleTelemetry,
            telemetryHelper = telemetryHelper,
            contacts = emptyList(),
            apps = emptyList(),
            preferredLanguage = "en-IN"
        )
        assertTrue(alarmOutcome is LocalRouteOutcome.Handled)
        val alarm = (alarmOutcome as LocalRouteOutcome.Handled).confirmation as PendingActionConfirmation.SetAlarm
        assertEquals(18, alarm.hour)
        assertEquals(30, alarm.minute)

        val chatOutcome = LocalCommandRouter.route(
            input = "Explain quantum computing simply",
            telemetry = sampleTelemetry,
            telemetryHelper = telemetryHelper,
            contacts = emptyList(),
            apps = listOf(InstalledAppInfo("Settings", "com.android.settings")),
            preferredLanguage = "en-IN"
        )
        assertTrue(chatOutcome is LocalRouteOutcome.ForwardToBackend)
    }
}
