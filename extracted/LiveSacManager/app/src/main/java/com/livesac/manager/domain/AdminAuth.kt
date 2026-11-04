package com.livesac.manager.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stockage clé/valeur (SharedPreferences privées sur Android, mémoire dans les tests). */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * Mot de passe administrateur : jamais stocké en clair. On conserve uniquement un sel aléatoire
 * et l'empreinte PBKDF2-HMAC-SHA256 (nombre d'itérations enregistré avec).
 */
class AdminAuth(private val store: SecretStore, private val iterations: Int = DEFAULT_ITERATIONS) {

    fun isConfigured(): Boolean = store.get(K_HASH) != null && store.get(K_SALT) != null

    /** Définit (ou remplace) le mot de passe. */
    fun setPassword(password: String) {
        require(password.length >= MIN_LENGTH) { "Le mot de passe doit contenir au moins $MIN_LENGTH caractères." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, iterations)
        store.put(K_SALT, Base64.getEncoder().encodeToString(salt))
        store.put(K_ITER, iterations.toString())
        store.put(K_HASH, Base64.getEncoder().encodeToString(hash))
    }

    fun verify(password: String): Boolean {
        val salt = store.get(K_SALT)?.let { Base64.getDecoder().decode(it) } ?: return false
        val expected = store.get(K_HASH)?.let { Base64.getDecoder().decode(it) } ?: return false
        val iter = store.get(K_ITER)?.toIntOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iter), expected)
    }

    /** Change le mot de passe si l'ancien est correct. */
    fun change(old: String, new: String): Boolean {
        if (!verify(old)) return false
        setPassword(new)
        return true
    }

    private fun derive(password: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val MIN_LENGTH = 4
        const val DEFAULT_ITERATIONS = 120_000
        private const val K_SALT = "salt"
        private const val K_HASH = "hash"
        private const val K_ITER = "iterations"
    }
}
