package com.example.data.local

import android.content.Context
import java.security.SecureRandom

object PairingIdentity {
    private const val PREFS = "child_identity"
    private const val CODE = "pairing_code"
    private const val TOKEN = "auth_token"
    private const val DEVICE_ID = "device_id"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pairingCode(context: Context): String {
        val p = prefs(context)
        return p.getString(CODE, null) ?: generate(context)
    }

    fun authToken(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(TOKEN, null)
        if (existing != null) return existing
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        p.edit().putString(TOKEN, token).apply()
        return token
    }

    fun deviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(DEVICE_ID, null)
        if (existing != null) return existing
        val id = "child-" + java.util.UUID.randomUUID().toString().take(12)
        p.edit().putString(DEVICE_ID, id).apply()
        return id
    }

    private fun generate(context: Context): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        val code = buildString { repeat(6) { append(chars[r.nextInt(chars.length)]) } }
        prefs(context).edit().putString(CODE, code).apply()
        return code
    }
}
