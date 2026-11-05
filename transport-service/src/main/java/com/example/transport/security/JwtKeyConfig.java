package com.example.transport.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Base64;

@Configuration
public class JwtKeyConfig {
    @Bean
    public KeyPair jwtKeyPair(
            ResourceLoader loader,
            @Value("${jwt.private-key}") String privateLocation,
            @Value("${jwt.public-key}") String publicLocation)
            throws Exception {
        if (!privateLocation.startsWith("file:") || !publicLocation.startsWith("file:"))
            throw new IllegalArgumentException("JWT keys must be external file: resources");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey =
                (RSAPrivateKey)
                        factory.generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        readPem(loader, privateLocation, "PRIVATE KEY")));
        RSAPublicKey publicKey =
                (RSAPublicKey)
                        factory.generatePublic(
                                new X509EncodedKeySpec(
                                        readPem(loader, publicLocation, "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus()))
            throw new IllegalArgumentException("Matching RSA keys of at least 2048 bits required");
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(new byte[] {1});
        byte[] signed = signature.sign();
        signature.initVerify(publicKey);
        signature.update(new byte[] {1});
        if (!signature.verify(signed))
            throw new IllegalArgumentException("JWT key pair does not match");
        return new KeyPair(publicKey, privateKey);
    }

    private byte[] readPem(ResourceLoader loader, String location, String type) throws Exception {
        // Dùng InputStream để tránh lỗi khi chạy từ JAR
        try (var input = loader.getResource(location).getInputStream()) {
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            return Base64.getDecoder()
                    .decode(
                            pem.replace("-----BEGIN " + type + "-----", "")
                                    .replace("-----END " + type + "-----", "")
                                    .replaceAll("\\s", ""));
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
        var key =
                new RSAKey.Builder((RSAPublicKey) jwtKeyPair.getPublic())
                        .privateKey(jwtKeyPair.getPrivate())
                        .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            KeyPair jwtKeyPair,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience) {
        var decoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtKeyPair.getPublic())
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        OAuth2TokenValidator<Jwt> requiredClaims =
                jwt -> {
                    try {
                        java.util.UUID.fromString(jwt.getSubject());
                        if (jwt.getExpiresAt() != null
                                && jwt.getIssuedAt() != null
                                && jwt.getAudience().contains(audience))
                            return OAuth2TokenValidatorResult.success();
                    } catch (IllegalArgumentException | NullPointerException ignored) {
                    }
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Invalid token claims", null));
                };
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer), requiredClaims));
        return decoder;
    }
}
