package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rohit.labelbuilder.data.DataException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class CredentialStoreTest {

    /** Compares content: {@code Optional<char[]>} equality would compare array identity. */
    private static void assertPassword(Optional<char[]> actual, String expected) {
        assertThat(actual).isPresent();
        assertThat(actual.orElseThrow()).isEqualTo(expected.toCharArray());
    }

    @Test
    void aStoreWithNothingInItIsNotAnError() {
        // Integrated security and a file-based database genuinely have no password.
        assertThat(CredentialStore.none().password("cfg-1")).isEmpty();
        assertThat(new InMemoryCredentialStore().password("cfg-1")).isEmpty();
    }

    @Test
    void aPasswordComesBackAsStored() {
        InMemoryCredentialStore store = new InMemoryCredentialStore("cfg-1", "s3cret".toCharArray());

        assertPassword(store.password("cfg-1"), "s3cret");
        assertThat(store.password("cfg-2")).isEmpty();
    }

    @Test
    void wipingWhatTheStoreHandedOutDoesNotEmptyTheStore() {
        // ConnectionManager wipes its copy after every connect; the next print run still needs the
        // password, so the copies have to be independent.
        InMemoryCredentialStore store = new InMemoryCredentialStore("cfg-1", "s3cret".toCharArray());

        Arrays.fill(store.password("cfg-1").orElseThrow(), '\0');

        assertPassword(store.password("cfg-1"), "s3cret");
    }

    @Test
    void wipingTheCallersArrayAfterStoringDoesNotEmptyTheStoreEither() {
        char[] password = "s3cret".toCharArray();
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        store.put("cfg-1", password);

        Arrays.fill(password, '\0');

        assertPassword(store.password("cfg-1"), "s3cret");
    }

    @Test
    void credentialsCanBeForgotten() {
        InMemoryCredentialStore store = new InMemoryCredentialStore("cfg-1", "s3cret".toCharArray());

        store.remove("cfg-1");

        assertThat(store.password("cfg-1")).isEmpty();
    }

    @Test
    void clearForgetsEverythingForASignOut() {
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        store.put("cfg-1", "a".toCharArray());
        store.put("cfg-2", "b".toCharArray());

        store.clear();

        assertThat(store.password("cfg-1")).isEmpty();
        assertThat(store.password("cfg-2")).isEmpty();
    }

    // ---- the encrypted-at-rest store ----

    @Test
    void aSealedStoreDecryptsOnlyWhenAsked() {
        SealedCredentialStore store = new SealedCredentialStore(SecretCipher.newKey());

        store.put("cfg-1", "s3cret".toCharArray());

        assertPassword(store.password("cfg-1"), "s3cret");
    }

    @Test
    void theBytesASealedStoreWouldPersistCarryNoPlaintext() {
        SealedCredentialStore store = new SealedCredentialStore(SecretCipher.newKey());

        byte[] onDisk = store.put("cfg-1", "s3cret".toCharArray());

        assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("s3cret");
        assertThat(store.sealedFor("cfg-1")).isPresent();
        assertThat(store.sealedFor("cfg-1").orElseThrow()).isEqualTo(onDisk);
    }

    @Test
    void sealedBytesReadBackFromStorageStillOpen() {
        // The Phase 16a round trip: rows and the key come from the client store, nothing is re-entered.
        SecretKey key = SecretCipher.newKey();
        byte[] onDisk = new SealedCredentialStore(key).put("cfg-1", "s3cret".toCharArray());

        SealedCredentialStore afterRestart = new SealedCredentialStore(key);
        afterRestart.load("cfg-1", onDisk);

        assertPassword(afterRestart.password("cfg-1"), "s3cret");
    }

    @Test
    void aSealedStoreWithTheWrongKeySuppliesNothingUsable() {
        byte[] onDisk = new SealedCredentialStore(SecretCipher.newKey()).put("cfg-1", "s3cret".toCharArray());
        SealedCredentialStore wrongKey = new SealedCredentialStore(SecretCipher.newKey());
        wrongKey.load("cfg-1", onDisk);

        // Not an empty Optional: "there is a credential here and it will not open" is a different
        // problem from "there is none", and silently trying no password would hide it.
        assertThatThrownBy(() -> wrongKey.password("cfg-1")).isInstanceOf(DataException.class);
    }

    @Test
    void eachSealedReadIsIndependentSoWipingOneDoesNotAffectTheNext() {
        SealedCredentialStore store = new SealedCredentialStore(SecretCipher.newKey());
        store.put("cfg-1", "s3cret".toCharArray());

        Arrays.fill(store.password("cfg-1").orElseThrow(), '\0');

        assertPassword(store.password("cfg-1"), "s3cret");
    }

    @Test
    void aSealedStoreForgetsOnRemove() {
        SealedCredentialStore store = new SealedCredentialStore(SecretCipher.newKey());
        store.put("cfg-1", "s3cret".toCharArray());

        store.remove("cfg-1");

        assertThat(store.password("cfg-1")).isEmpty();
        assertThat(store.sealedFor("cfg-1")).isEmpty();
    }
}
