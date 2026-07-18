package com.netlink.onemep_feature.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the RS256 public key issued by the identity service. Only the public key is needed here —
 * this service validates tokens, it never signs them.
 */
@Configuration
@Slf4j
public class PublicKeyConfig {

  @Bean
  public RSAPublicKey publicKey(@Value("${jwt.public-key}") String publicKeyPath) {
    try {
      String key = Files.readString(Path.of(publicKeyPath), StandardCharsets.UTF_8);
      key =
          key.replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(key);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      log.info("RSA public key loaded successfully from {}", publicKeyPath);
      return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    } catch (Exception ex) {
      log.error("Failed to load RSA public key from path: {}", publicKeyPath, ex);
      throw new IllegalStateException(
          "Failed to load RSA public key. Application startup aborted.", ex);
    }
  }
}
