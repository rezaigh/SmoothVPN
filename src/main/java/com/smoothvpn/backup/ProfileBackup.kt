package com.smoothvpn.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup / restore of all profiles as a single portable file, optionally
 * passphrase-encrypted (AES-256-GCM, PBKDF2). So users never lose their configs.
 *
 * Hook your repository in: serialize your profiles to a JSON array, pass it to
 * [export]; write the returned string to a file via the Storage Access Framework
 * (ACTION_CREATE_DOCUMENT). On restore, read the file and pass it to [import].
 *
 *   val blob = ProfileBackup.export(profilesJsonArrayString, passphrase)   // -> save
 *   val json = ProfileBackup.import(blob, passphrase)                      // -> re-add
 */
object ProfileBackup {

    private const val MAGIC = "SMOOTHVPN1"
    private const val ITER = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /** @param profilesJson a JSON array string of your exported profiles. */
    fun export(profilesJson: String, passphrase: String?): String {
        val payload = JSONObject().apply {
            put("magic", MAGIC)
            put("profiles", org.json.JSONArray(profilesJson))
        }.toString()

        if (passphrase.isNullOrEmpty()) {
            return "PLAIN:" + Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ct = cipher.doFinal(payload.toByteArray())
        return "ENC:" + b64(salt) + ":" + b64(iv) + ":" + b64(ct)
    }

    /** Returns the JSON array string of profiles. Throws on bad passphrase/format. */
    fun import(blob: String, passphrase: String?): String {
        val text = when {
            blob.startsWith("PLAIN:") ->
                String(Base64.decode(blob.removePrefix("PLAIN:"), Base64.NO_WRAP))
            blob.startsWith("ENC:") -> {
                require(!passphrase.isNullOrEmpty()) { "Passphrase required" }
                val (_, s, iv, ct) = blob.split(":")
                val key = deriveKey(passphrase, Base64.decode(s, Base64.NO_WRAP))
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key,
                        GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
                }
                String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
            }
            else -> error("Unrecognized backup file")
        }
        val obj = JSONObject(text)
        require(obj.optString("magic") == MAGIC) { "Not a SmoothVPN backup" }
        return obj.getJSONArray("profiles").toString()
    }

    private fun deriveKey(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITER, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private operator fun <T> List<T>.component4() = this[3]
}
