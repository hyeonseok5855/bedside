package com.bedside.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SQLCipher DB 패스프레이즈 관리 (decisions.md 15).
 *
 * 위협 모델: (a) 분실·도난 시 저장소 덤프, (b) 앱 삭제 후 잔존 파일, (c) 백업 유출.
 * "폰을 잠금 해제해 손에 쥔 사람"은 막지 않는다 → **사용자 인증을 요구하지 않는다**
 * (밤 UX와 충돌하지 않게).
 *
 * 구현: 무작위 32바이트 패스프레이즈를 만들어, Android Keystore의 AES/GCM 키로
 * 암호화해 SharedPreferences에 보관한다. Keystore 키는 기기를 떠나지 않는다.
 */
object DbKey {

    private const val KEY_ALIAS = "bedside_db_key"
    private const val PREFS = "db_key"
    private const val PREF_WRAPPED = "wrapped_passphrase"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(PREF_WRAPPED, null)?.let { return unwrap(it) }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString(PREF_WRAPPED, wrap(passphrase)) }
        return passphrase
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(passphrase)
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val ct = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // setUserAuthenticationRequired 를 두지 않는다 — 인증 없음(결정 15).
                .build(),
        )
        return generator.generateKey()
    }
}
