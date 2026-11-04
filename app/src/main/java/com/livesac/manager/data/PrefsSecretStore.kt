package com.livesac.manager.data

import android.content.Context
import com.livesac.manager.domain.SecretStore

/** Stockage privé à l'application (inaccessible aux autres applications). N'y est écrite que l'empreinte salée du mot de passe. */
class PrefsSecretStore(context: Context) : SecretStore {
    private val prefs = context.applicationContext.getSharedPreferences("admin_security", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }
}
