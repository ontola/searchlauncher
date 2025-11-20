package com.searchlauncher.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class MainActivityKtTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shadowAppOps =
        Shadows.shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
    private val uid = Process.myUid()
    private val pkg = context.packageName

    @Test
    fun `returns true when permission is allowed`() {
        // Set the shadow system state to ALLOWED
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            uid,
            pkg,
            AppOpsManager.MODE_ALLOWED
        )

        assertTrue(hasUsageStatsPermission(context))
    }

    @Test
    fun `returns false when permission is NOT allowed`() {
        // MODE_IGNORED
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            uid,
            pkg,
            AppOpsManager.MODE_IGNORED
        )
        assertFalse(hasUsageStatsPermission(context))

        //  MODE_ERRORED
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            uid,
            pkg,
            AppOpsManager.MODE_ERRORED
        )
        assertFalse(hasUsageStatsPermission(context))

        //  MODE_DEFAULT
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            uid,
            pkg,
            AppOpsManager.MODE_DEFAULT
        )
        assertFalse(hasUsageStatsPermission(context))
    }

    @Test
    fun `returns false when system service throws exception or is null`() {
        val mockContext = io.mockk.mockk<Context>()

        // getSystemService returns null or throws
        io.mockk.every { mockContext.getSystemService(any()) } throws SecurityException("Exception!")

        // Should return false
        assertFalse(hasUsageStatsPermission(mockContext))
    }

    @Test
    fun `returns true when this app accessibility service is enabled`() {
        Settings.Secure.putString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            pkg
        )

        assertTrue(isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `returns false when other accessibility services are enabled but not this one`() {
        assertFalse(isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `returns false when the enabled services string is null or empty`() {
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        assertTrue(services.isNullOrEmpty())
        assertFalse(isAccessibilityServiceEnabled(context))
    }

}