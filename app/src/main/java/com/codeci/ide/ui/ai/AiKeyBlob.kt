package com.codeci.ide.ui.ai

/**
 * Phase 76 — the on-disk shape of the encrypted key: `[version][ivLen][iv][ciphertext]`.
 * Pure so the format is host-tested (`AiKeyStoreFormatTest`); the crypto
 * itself is Android Keystore's.
 */
object AiKeyBlob {
    const val VERSION: Byte = 1

    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size in 1..255) { "bad iv" }
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    /** (iv, ciphertext), or null for anything this version did not write. */
    fun decode(blob: ByteArray): Pair<ByteArray, ByteArray>? {
        if (blob.size < 3 || blob[0] != VERSION) return null
        val ivLen = blob[1].toInt() and 0xFF
        if (ivLen == 0 || blob.size <= 2 + ivLen) return null
        return blob.copyOfRange(2, 2 + ivLen) to blob.copyOfRange(2 + ivLen, blob.size)
    }
}
