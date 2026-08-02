package com.medisense.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.medisense.app.data.remote.firebase.FirebaseAuthService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService,
    private val firestore: FirebaseFirestore
) {

    fun isUserLoggedIn(): Boolean = firebaseAuthService.isUserLoggedIn()

    suspend fun login(email: String, password: String): Boolean {
        return firebaseAuthService.login(email, password)
    }

    suspend fun register(email: String, password: String, fullName: String): Boolean {
        val uid = firebaseAuthService.register(email, password)
        
        // Create user profile in Firestore
        val userMap = hashMapOf(
            "uid" to uid,
            "fullName" to fullName,
            "email" to email,
            "createdAt" to System.currentTimeMillis()
        )
        
        firestore.collection("profiles")
            .document(uid)
            .set(userMap)
            .await()
            
        return true
    }

    suspend fun resetPassword(email: String): Boolean {
        return firebaseAuthService.resetPassword(email)
    }

    fun logout() {
        firebaseAuthService.logout()
    }
}
