package com.medisense.app.di

import com.medisense.app.BuildConfig
import com.medisense.app.data.local.session.SharedPreferencesSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        sessionManager: SharedPreferencesSessionManager
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                this.sessionManager = sessionManager
                this.alwaysAutoRefresh = true
                this.autoLoadFromStorage = true
            }
            install(Postgrest)
        }
    }
}
