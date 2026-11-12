package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretKey key = SecretCipher.newKey();

    @Test
    void aSealedPasswordOpensBackToItself() {
        char[] sealed = SecretCipher.open(key, SecretCipher.seal(key, "s3cret-p@ss".toCharArray()));

        assertThat(sealed).isEqualTo("s3cret-p@ss".toCharArray());
    }

    @Test
    void nonAsciiPasswordsSurviveTheRoundTrip() {
        char[] password = "pä$$wörd-ção".toCharArray();

        assertThat(SecretCipher.open(key, SecretCipher.seal(key, password))).isEqualTo(password);
    }

    @Test
    void anEmptyPasswordIsStillAValidSecret() {
        assertThat(SecretCipher.open(key, SecretCipher.seal(key, new char[0]))).isEmpty();
    }

    @Test
    void sealingTheSameValueTwiceProducesDifferentBytes() {
        // A fresh IV per encryption is not an optimisation: reusing one under the same key breaks GCM
        // outright. Identical ciphertexts would also tell an attacker which accounts share a password.
        byte[] first = SecretCipher.seal(key, "same".toCharArray());
        byte[] second = SecretCipher.seal(key, "same".toCharArray());

        assertThat(first).isNotEqualTo(second);
        assertThat(SecretCipher.open(key, first)).isEqualTo(SecretCipher.open(key, second));
    }

    @Test
    void aTamperedCiphertextRefusesToOpen() {
        // The reason for GCM over CBC: altered bytes must not decrypt to plausible rubbish that then
        // gets sent to a production database as a password.
        byte[] sealed = SecretCipher.seal(key, "s3cret".toCharArray());
        sealed[sealed.length - 1] ^= 0x01;

        assertThatThrownBy(() -> SecretCipher.open(key, sealed))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("corrupt");
    }

    @Test
    void aTamperedInitialisationVectorRefusesToOpenToo() {
        byte[] sealed = SecretCipher.seal(key, "s3cret".toCharArray());
        sealed[0] ^= 0x01;

        assertThatThrownBy(() -> SecretCipher.open(key, sealed)).isInstanceOf(DataException.class);
    }

    @Test
    void theWrongKeyDoesNotOpenIt() {
        byte[] sealed = SecretCipher.seal(key, "s3cret".toCharArray());

        assertThatThrownBy(() -> SecretCipher.open(SecretCipher.newKey(), sealed))
                .isInstanceOf(DataException.class);
    }

    @Test
    void truncatedStoredBytesAreReportedRatherThanIndexed() {
        assertThatThrownBy(() -> SecretCipher.open(key, new byte[8]))
                .isInstanceOf(DataException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void aKeyCanBeStoredAndRebuiltFromItsBytes() {
        // What a persistent store does: keep the raw key wherever it keeps it, and rebuild it on start.
        byte[] raw = key.getEncoded();
        byte[] sealed = SecretCipher.seal(key, "s3cret".toCharArray());

        assertThat(SecretCipher.open(SecretCipher.keyFrom(raw), sealed)).isEqualTo("s3cret".toCharArray());
    }

    @Test
    void aKeyOfTheWrongLengthIsRefused() {
        assertThatThrownBy(() -> SecretCipher.keyFrom(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void theCallersArrayIsLeftAloneSoItCanDecideWhenToWipeIt() {
        char[] password = "s3cret".toCharArray();

        SecretCipher.seal(key, password);

        assertThat(password).isEqualTo("s3cret".toCharArray());
        Arrays.fill(password, '\0');
    }

    @Test
    void theSealedFormIsNotTheSecretInDisguise() {
        byte[] sealed = SecretCipher.seal(key, "s3cret".toCharArray());

        assertThat(new String(sealed, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("s3cret");
    }
}
