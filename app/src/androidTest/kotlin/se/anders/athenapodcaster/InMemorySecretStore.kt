package se.anders.athenapodcaster

import java.util.UUID
import se.anders.athenapodcaster.data.secrets.SecretStore

/** Test double for the Keystore-backed store. */
class InMemorySecretStore : SecretStore {
    val values = mutableMapOf<String, String>()
    override fun put(value: String): String = UUID.randomUUID().toString().also { values[it] = value }
    override fun get(ref: String): String? = values[ref]
    override fun replace(ref: String, value: String) { values[ref] = value }
    override fun delete(ref: String) { values.remove(ref) }
}
