package com.rohit.labelbuilder.data.jdbc;

import com.rohit.labelbuilder.data.DataException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals and opens a stored password with AES-GCM, so credentials are encrypted at rest (FR-DS-09).
 *
 * <p>This is the cryptography only; <b>where the key lives is deliberately not decided here</b>. The
 * client's encrypted store arrives with the H2 local store in Phase 16a and the server's in Phase 21,
 * and each will hold its key differently — an OS keychain on the desktop, a deployment secret on the
 * server. Keeping the cipher separate means that choice can be made twice, in the right places,
 * without reopening the question of how a secret is encrypted.
 *
 * <p>GCM rather than CBC because it authenticates: a sealed value that has been tampered with fails to
 * open instead of decrypting to plausible rubbish, so a corrupted store cannot quietly become a wrong
 * password sent to a production database. A fresh random IV per encryption is required — reusing one
 * under the same key breaks GCM catastrophically — so one is generated each time and prefixed to the
 * ciphertext rather than being left for the caller to manage.
 */
public final class SecretCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12; // the size GCM is specified for; others are slower and weaker
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCipher() {}

    /** A new random 256-bit key, for a store that has none yet. */
    public static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(KEY_BITS);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new DataException("this JVM cannot generate an AES key", e);
        }
    }

    /** Wraps raw key bytes, as a store would read them back from wherever it keeps them. */
    public static SecretKey keyFrom(byte[] raw) {
        if (raw.length != KEY_BITS / 8) {
            throw new IllegalArgumentException(
                    "an AES-" + KEY_BITS + " key is " + KEY_BITS / 8 + " bytes, got " + raw.length);
        }
        return new SecretKeySpec(raw, ALGORITHM);
    }

    /**
     * Seals a password: {@code [12-byte IV][ciphertext+tag]}.
     *
     * <p>The caller's array is left untouched; wiping it is the caller's business, since only it knows
     * when the value is no longer needed.
     */
    public static byte[] seal(SecretKey key, char[] secret) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        byte[] plaintext = toUtf8(secret);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] sealed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, sealed, 0, iv.length);
            System.arraycopy(ciphertext, 0, sealed, iv.length, ciphertext.length);
            return sealed;
        } catch (GeneralSecurityException e) {
            throw new DataException("cannot encrypt the credential", e);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /**
     * Opens a sealed password.
     *
     * @throws DataException if the key is wrong or the stored bytes have been altered — GCM cannot
     *     tell those apart, and neither case should produce a password to try against a database
     */
    public static char[] open(SecretKey key, byte[] sealed) {
        if (sealed.length <= IV_BYTES) {
            throw new DataException("the stored credential is truncated");
        }
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            plaintext = cipher.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
            return fromUtf8(plaintext);
        } catch (GeneralSecurityException e) {
            throw new DataException(
                    "the stored credential cannot be decrypted; it is corrupt or sealed" + " with a different key", e);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    /** Encodes without going through a String, which would outlive the call in the heap. */
    private static byte[] toUtf8(char[] chars) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private static char[] fromUtf8(byte[] bytes) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = new char[decoded.remaining()];
        decoded.get(chars);
        if (decoded.hasArray()) {
            Arrays.fill(decoded.array(), '\0');
        }
        return chars;
    }
}
