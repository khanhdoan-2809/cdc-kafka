package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.*;
import java.security.*;

class JwtKeyConfigTest {
    @TempDir Path directory;

    KeyPair pair(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    String save(String name, String type, byte[] bytes) throws Exception {
        Path file = directory.resolve(name);
        AuthenticationIntegrationTest.pem(file, type, bytes);
        return file.toUri().toString();
    }

    @Test
    void externalMatchingKeysLoadAndClasspathIsRejected() throws Exception {
        var pair = pair(2048);
        String privateKey = save("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicKey = save("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        var config = new JwtKeyConfig();
        assertThat(
                        config.jwtKeyPair(new DefaultResourceLoader(), privateKey, publicKey)
                                .getPublic())
                .isEqualTo(pair.getPublic());
        assertThatThrownBy(
                        () ->
                                config.jwtKeyPair(
                                        new DefaultResourceLoader(),
                                        "classpath:private.pem",
                                        publicKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weakMismatchedAndMissingKeysFailClosed() throws Exception {
        var config = new JwtKeyConfig();
        var loader = new DefaultResourceLoader();
        var weak = pair(1024);
        String weakPrivate =
                save("weak-private.pem", "PRIVATE KEY", weak.getPrivate().getEncoded());
        String weakPublic = save("weak-public.pem", "PUBLIC KEY", weak.getPublic().getEncoded());
        assertThatThrownBy(() -> config.jwtKeyPair(loader, weakPrivate, weakPublic))
                .isInstanceOf(IllegalArgumentException.class);
        String privateKey =
                save("private.pem", "PRIVATE KEY", pair(2048).getPrivate().getEncoded());
        String publicKey = save("public.pem", "PUBLIC KEY", pair(2048).getPublic().getEncoded());
        assertThatThrownBy(() -> config.jwtKeyPair(loader, privateKey, publicKey))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                config.jwtKeyPair(
                                        loader,
                                        directory.resolve("missing").toUri().toString(),
                                        publicKey))
                .isInstanceOf(java.io.IOException.class);
    }
}
