package com.medisense.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.medisense.app.data.remote.firebase.FirebaseAuthService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService,
    private val firestore: FirebaseFirestore
) {

    suspend fun getUserProfile(): Map<String, Any>? {
        val uid = firebaseAuthService.getCurrentUserId() ?: return null
        
        return try {
            // Try to fetch from server or cache
            val document = firestore.collection("profiles")
                .document(uid)
                .get()
                .await()
                
            document.data
        } catch (e: Exception) {
            // Fallback to cache if offline and default get() fails
            try {
                val cachedDocument = firestore.collection("profiles")
                    .document(uid)
                    .get(Source.CACHE)
                    .await()
                cachedDocument.data
            } catch (cacheException: Exception) {
                null
            }
        }
    }

    fun logout() {
        firebaseAuthService.logout()
    }
}
