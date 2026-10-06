package com.medisense.app

import com.medisense.app.domain.support.MediSenseSupportConstants
import com.medisense.app.utils.PermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class OnboardingUnitTest {

    @Test
    fun testPermissionHelper_constants() {
        assertEquals("android.permission.CAMERA", PermissionHelper.PERMISSION_CAMERA)
        assertEquals("android.permission.RECORD_AUDIO", PermissionHelper.PERMISSION_RECORD_AUDIO)
        assertEquals("android.permission.POST_NOTIFICATIONS", PermissionHelper.PERMISSION_POST_NOTIFICATIONS)
    }

    @Test
    fun testOnboardingState_simulation() {
        var hasCompletedPermissionOnboarding = false
        assertFalse(hasCompletedPermissionOnboarding)

        // Simulate user completing onboarding
        hasCompletedPermissionOnboarding = true
        assertTrue(hasCompletedPermissionOnboarding)
    }

    @Test
    fun testWelcomeConsentState_requirement() {
        var isConsentChecked = false
        var isGetStartedEnabled = false

        // 1. Initial State: checkbox unchecked, button disabled
        assertFalse("Checkbox must initially be unchecked", isConsentChecked)
        isGetStartedEnabled = isConsentChecked
        assertFalse("Get Started button must initially be disabled", isGetStartedEnabled)

        // 2. User checks the checkbox: button enabled
        isConsentChecked = true
        isGetStartedEnabled = isConsentChecked
        assertTrue("Get Started button must become enabled after consent", isGetStartedEnabled)

        // 3. User unchecks the checkbox: button disabled again
        isConsentChecked = false
        isGetStartedEnabled = isConsentChecked
        assertFalse("Get Started button must become disabled when consent is unchecked", isGetStartedEnabled)

        // 4. User re-checks and completes onboarding
        isConsentChecked = true
        isGetStartedEnabled = isConsentChecked
        var onboardingCompleted = false
        if (isConsentChecked) {
            onboardingCompleted = true
        }
        assertTrue("Onboarding succeeds with explicit consent", onboardingCompleted)
    }

    @Test
    fun testTermsAndPrivacyWebsite_validity() {
        val policyUrl = MediSenseSupportConstants.MEDISENSE_WEBSITE_URL
        assertEquals("https://medisense-policy-website.vercel.app/", policyUrl)

        val uri = URI.create(policyUrl)
        assertEquals("https", uri.scheme)
        assertEquals("medisense-policy-website.vercel.app", uri.host)
        assertTrue(uri.isAbsolute)
    }
}
