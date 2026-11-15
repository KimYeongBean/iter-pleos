package io.github.kimyeongbean.iter.vehicle

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "RecordDatabaseEncryption"

/**
 * Encryption of the record database (ADR 0003, #126): the whole SQLite file is encrypted with
 * SQLCipher. Its passphrase is random, made once per install, and kept only wrapped by an AES key
 * in the Android Keystore, so the passphrase never sits on disk in the clear.
 *
 * A database left from before encryption is encrypted in place, keeping its records. If the
 * passphrase can no longer be unwrapped (the Keystore was reset), the unreadable database is
 * deleted and a new one starts, instead of the app failing to open.
 */
internal object RecordDatabaseEncryption {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "iter_record_db"
    private const val PREFS = "record_db_key"
    private const val PREF_WRAPPED = "wrapped"
    private const val PREF_IV = "iv"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * True on the JVM test runner (Robolectric), where the SQLCipher native library cannot load.
     * Only there the database stays plain; a real device always encrypts.
     */
    private val onJvmTestRunner get() = Build.FINGERPRINT == "robolectric"

    /** The open helper factory for the encrypted database named [databaseName], or null on the JVM test runner. */
    fun openHelperFactory(
        context: Context,
        databaseName: String,
    ): SupportSQLiteOpenHelper.Factory? {
        if (onJvmTestRunner) return null
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(databaseName)
        val passphrase = storedPassphrase(context) ?: newPassphrase(context)
        when {
            isPlain(file) -> {
                // If encrypting fails, keep the records readable this run and try again next open.
                val encrypted = runCatching { encryptInPlace(file, passphrase) }
                encrypted.exceptionOrNull()?.let {
                    Log.w(TAG, "record database could not be encrypted; opening it plain this time", it)
                    return null
                }
            }

            file.exists() && !opens(file, passphrase) -> {
                Log.w(TAG, "record database cannot be opened with the stored key; starting a new one")
                deleteDatabaseFiles(file)
            }
        }
        return SupportOpenHelperFactory(passphrase.toByteArray(Charsets.US_ASCII))
    }

    /** True when [file] is an unencrypted SQLite database (it starts with the plain SQLite header). */
    internal fun isPlain(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(header) }
        return header.contentEquals(SQLITE_HEADER)
    }

    /**
     * Copies the plain database at [file] into an encrypted one next to it, checks the copy opens
     * with [passphrase], then renames it over [file]. The plain file stays until that rename, so a
     * failure or a kill before it leaves the records as they were.
     */
    private fun encryptInPlace(
        file: File,
        passphrase: String,
    ) {
        val encrypted = File(file.path + ".encrypting")
        deleteDatabaseFiles(encrypted)
        try {
            // An empty passphrase opens a plain database; closing it folds the WAL into the file.
            // CREATE_IF_NECESSARY: ATTACH opens with these flags and must create the encrypted file.
            val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
            SQLiteDatabase.openDatabase(file.path, "", null, flags, null, null).use { plain ->
                val version = plain.version
                plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf<Any>(encrypted.path, passphrase))
                plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                // sqlcipher_export does not copy user_version, which Room reads as the schema version.
                plain.execSQL("PRAGMA encrypted.user_version = $version")
                plain.execSQL("DETACH DATABASE encrypted")
            }
            check(opens(encrypted, passphrase)) { "encrypted copy does not open" }
            listOf("-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
            check(encrypted.renameTo(file)) { "could not move the encrypted record database in place" }
        } finally {
            deleteDatabaseFiles(encrypted)
        }
        Log.i(TAG, "record database encrypted in place")
    }

    private fun opens(
        file: File,
        passphrase: String,
    ): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
        }
    }.isSuccess

    private fun deleteDatabaseFiles(file: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    /** The passphrase made earlier, or null if there is none or it can no longer be unwrapped. */
    private fun storedPassphrase(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(PREF_WRAPPED, null) ?: return null
        val iv = prefs.getString(PREF_IV, null) ?: return null
        return runCatching {
            val key = keystoreKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), Charsets.US_ASCII)
        }.onFailure { Log.w(TAG, "stored record database key cannot be unwrapped", it) }
            .getOrNull()
    }

    /** Makes a random passphrase, keeps it wrapped by a new Keystore key, and returns it. */
    private fun newPassphrase(context: Context): String {
        val bytes = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKeystoreKey())
        val wrapped = cipher.doFinal(passphrase.toByteArray(Charsets.US_ASCII))
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        return passphrase
    }

    private fun keystoreKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun newKeystoreKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
