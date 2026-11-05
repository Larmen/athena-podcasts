package se.anders.athenapodcaster.data.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds secrets, in practice private feed URLs (they contain the access token; constitution §4). The database
 * stores only the opaque reference returned by [put].
 */
interface SecretStore {
    fun put(value: String): String
    fun get(ref: String): String?
    fun replace(ref: String, value: String)
    fun delete(ref: String)
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it. Each secret is one small
 * file in the app's private storage: 12-byte IV followed by the ciphertext. Nothing here logs a value.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val dir = File(context.applicationContext.filesDir, "secrets").apply { mkdirs() }

    override fun put(value: String): String {
        val ref = UUID.randomUUID().toString()
        write(ref, value)
        return ref
    }

    override fun get(ref: String): String? {
        val file = fileFor(ref).takeIf { it.isFile } ?: return null
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            }
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            null // unreadable (for example the key was lost): treated as missing, never a crash
        }
    }

    override fun replace(ref: String, value: String) = write(ref, value)

    override fun delete(ref: String) {
        fileFor(ref).delete()
    }

    private fun write(ref: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val target = fileFor(ref)
        val temp = File(dir, "$ref.tmp")
        temp.writeBytes(cipher.iv + encrypted)
        if (!temp.renameTo(target)) { temp.delete(); error("Could not store secret") }
    }

    /** Refs are generated here, but guard against path tricks anyway. */
    private fun fileFor(ref: String): File = File(dir, ref.filter { it.isLetterOrDigit() || it == '-' })

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "athena-podcaster-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
