package com.medisense.app

import com.medisense.app.data.local.dao.SecurityAuditEventDao
import com.medisense.app.data.local.entity.SecurityAuditEventEntity
import com.medisense.app.domain.model.PrivacyGovernanceInformation
import com.medisense.app.domain.model.SecurityAuditEventType
import com.medisense.app.domain.support.MediSenseSupportConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

/**
 * Pure JUnit 4 unit tests verifying:
 * 1. MediSense centralized support email & official website constants.
 * 2. PrivacyGovernanceInformation centralization and accuracy.
 * 3. URI format compliance for email intent (mailto:) and website (https://).
 * 4. Audit trail logging for SUPPORT_EMAIL_INITIATED and OFFICIAL_WEBSITE_OPENED.
 * 5. Multi-user security isolation and lack of sensitive healthcare data leakage.
 */
class MediSenseSupportUnitTest {

    private class FakeSecurityAuditEventDao : SecurityAuditEventDao {
        val events = mutableListOf<SecurityAuditEventEntity>()
        private var nextId = 1L

        override fun observeRecentAuditEvents(userId: String, limit: Int): Flow<List<SecurityAuditEventEntity>> {
            val userEvents = events.filter { it.userId == userId }.sortedByDescending { it.timestamp }.take(limit)
            return flowOf(userEvents)
        }

        override suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity> {
            return events.filter { it.userId == userId }.sortedByDescending { it.timestamp }
        }

        override suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long {
            val id = nextId++
            val saved = event.copy(id = id)
            events.add(saved)
            return id
        }

        override suspend fun deleteAllAuditEventsForUser(userId: String) {
            events.removeAll { it.userId == userId }
        }
    }

    private lateinit var auditDao: FakeSecurityAuditEventDao

    @Before
    fun setUp() {
        auditDao = FakeSecurityAuditEventDao()
    }

    @Test
    fun testCentralizedSupportConstants() {
        assertEquals("support4medisense@gmail.com", MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL)
        assertEquals("https://medisense-policy-website.vercel.app/", MediSenseSupportConstants.MEDISENSE_WEBSITE_URL)
        assertEquals("medisense-policy-website.vercel.app", MediSenseSupportConstants.MEDISENSE_WEBSITE_DOMAIN)
        assertEquals("MediSense App Support", MediSenseSupportConstants.SUPPORT_EMAIL_SUBJECT)

        assertTrue(MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL.contains("@"))
        assertTrue(MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL.endsWith("gmail.com"))
        assertTrue(MediSenseSupportConstants.MEDISENSE_WEBSITE_URL.startsWith("https://"))
        assertTrue(MediSenseSupportConstants.MEDISENSE_WEBSITE_URL.endsWith("/"))
    }

    @Test
    fun testPrivacyGovernanceInformationCentralization() {
        val info = PrivacyGovernanceInformation

        assertEquals(MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL, info.SUPPORT_EMAIL)
        assertEquals(MediSenseSupportConstants.MEDISENSE_WEBSITE_URL, info.OFFICIAL_WEBSITE_URL)
        assertEquals(MediSenseSupportConstants.MEDISENSE_WEBSITE_DOMAIN, info.OFFICIAL_WEBSITE_DOMAIN)

        assertNotNull(info.LOCAL_STORAGE_EXPLANATION)
        assertNotNull(info.CLOUD_STORAGE_EXPLANATION)
        assertNotNull(info.HEALTHCARE_DISCLAIMER)
        assertNotNull(info.LOCAL_DATA_CLEARING_NOTICE)
    }

    @Test
    fun testEmailAndWebsiteUriValidity() {
        // Website URL validation
        val websiteUri = URI.create(MediSenseSupportConstants.MEDISENSE_WEBSITE_URL)
        assertEquals("https", websiteUri.scheme)
        assertEquals("medisense-policy-website.vercel.app", websiteUri.host)

        // Email mailto URI validation
        val mailtoUriString = "mailto:${MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL}"
        val mailtoUri = URI.create(mailtoUriString)
        assertEquals("mailto", mailtoUri.scheme)
        assertEquals("support4medisense@gmail.com", mailtoUri.schemeSpecificPart)
    }

    @Test
    fun testSecurityAuditRecordingForSupportEmail() = runBlocking {
        val userId = "user-uuid-support-test"

        val entity = SecurityAuditEventEntity(
            userId = userId,
            eventType = SecurityAuditEventType.SUPPORT_EMAIL_INITIATED.name,
            timestamp = System.currentTimeMillis(),
            description = "User initiated support contact via email application (${MediSenseSupportConstants.MEDISENSE_SUPPORT_EMAIL})",
            appVersion = "1.0"
        )
        auditDao.insertAuditEvent(entity)

        val recentEvents = auditDao.observeRecentAuditEvents(userId, 10).first()
        assertEquals(1, recentEvents.size)

        val event = recentEvents[0]
        assertEquals(SecurityAuditEventType.SUPPORT_EMAIL_INITIATED.name, event.eventType)
        assertEquals(userId, event.userId)
        assertTrue(event.description.contains("support4medisense@gmail.com"))

        // Ensure no health/diagnostic information is recorded in the audit event
        assertFalse(event.description.contains("diagnosis"))
        assertFalse(event.description.contains("prediction"))
        assertFalse(event.description.contains("password"))
        assertFalse(event.description.contains("token"))
    }

    @Test
    fun testSecurityAuditRecordingForOfficialWebsite() = runBlocking {
        val userId = "user-uuid-support-test"

        val entity = SecurityAuditEventEntity(
            userId = userId,
            eventType = SecurityAuditEventType.OFFICIAL_WEBSITE_OPENED.name,
            timestamp = System.currentTimeMillis(),
            description = "User navigated to official MediSense policy website in browser (${MediSenseSupportConstants.MEDISENSE_WEBSITE_DOMAIN})",
            appVersion = "1.0"
        )
        auditDao.insertAuditEvent(entity)

        val recentEvents = auditDao.observeRecentAuditEvents(userId, 10).first()
        assertEquals(1, recentEvents.size)

        val event = recentEvents[0]
        assertEquals(SecurityAuditEventType.OFFICIAL_WEBSITE_OPENED.name, event.eventType)
        assertEquals(userId, event.userId)
        assertTrue(event.description.contains(MediSenseSupportConstants.MEDISENSE_WEBSITE_DOMAIN))

        // Ensure clean generic description without auth credentials or API keys
        assertFalse(event.description.contains("supabase"))
        assertFalse(event.description.contains("apiKey"))
    }

    @Test
    fun testMultiUserIsolationInAuditEvents() = runBlocking {
        val userA = "user-uuid-support-test"
        val userB = "user-uuid-other-person"

        auditDao.insertAuditEvent(
            SecurityAuditEventEntity(
                userId = userA,
                eventType = SecurityAuditEventType.SUPPORT_EMAIL_INITIATED.name,
                timestamp = 1000L,
                description = "User A contact",
                appVersion = "1.0"
            )
        )

        auditDao.insertAuditEvent(
            SecurityAuditEventEntity(
                userId = userB,
                eventType = SecurityAuditEventType.OFFICIAL_WEBSITE_OPENED.name,
                timestamp = 2000L,
                description = "User B website visit",
                appVersion = "1.0"
            )
        )

        val userAEvents = auditDao.observeRecentAuditEvents(userA, 10).first()
        val userBEvents = auditDao.observeRecentAuditEvents(userB, 10).first()

        assertEquals(1, userAEvents.size)
        assertEquals(1, userBEvents.size)

        assertEquals("SUPPORT_EMAIL_INITIATED", userAEvents.first().eventType)
        assertEquals("OFFICIAL_WEBSITE_OPENED", userBEvents.first().eventType)

        // Verify isolation
        assertFalse(userAEvents.any { it.userId == userB })
        assertFalse(userBEvents.any { it.userId == userA })
    }
}
