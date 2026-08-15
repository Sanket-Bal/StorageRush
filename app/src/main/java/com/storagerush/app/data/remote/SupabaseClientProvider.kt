package com.storagerush.app.data.remote

import com.storagerush.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single shared Supabase client for the app.
 * Object (not class) since we only ever need one instance —
 * matches the app's existing "no DI framework" convention.
 */
object SupabaseClientProvider {

    // Sourced from local.properties (supabase.url / supabase.anonKey) via
    // BuildConfig fields declared in app/build.gradle.kts — never
    // hardcoded here, and local.properties is git-ignored, so this stays
    // out of version control. The anon key is still meant to be public
    // per Supabase's own model (RLS is what actually protects the data);
    // this move is about not leaving secrets sitting in tracked source
    // files as a matter of hygiene, not because the anon key is secret.
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}