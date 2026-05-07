package com.spatium.deamon.db.temi.core

import android.util.Log
import com.spatium.deamon.db.temi.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"

    @Volatile
    private var client: SupabaseClient? = null

    fun getClient(): SupabaseClient? {
        client?.let { return it }

        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY

        if (url.isBlank() || anonKey.isBlank()) {
            Log.e(TAG, "SUPABASE_URL o SUPABASE_ANON_KEY vacíos; revisa local.properties")
            return null
        }

        return synchronized(this) {
            client ?: run {
                Log.d(TAG, "Inicializando SupabaseClient...")
                val created = createSupabaseClient(
                    supabaseUrl = url,
                    supabaseKey = anonKey,
                ) {
                    install(Postgrest) {
                        defaultSchema = "public"
                        // Usar nombres de columna exactos vía @SerialName cuando sea necesario
                        propertyConversionMethod = PropertyConversionMethod.SERIAL_NAME
                    }
                    install(Realtime) {
                        // Configuración por defecto; Realtime se usará para Postgres Changes
                    }
                }
                client = created
                created
            }
        }
    }
}
