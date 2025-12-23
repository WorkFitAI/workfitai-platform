package org.workfitai.authservice.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;

public class KeyGenerator {

  private static final Logger logger = LoggerFactory.getLogger(KeyGenerator.class);

  public static void main(String[] args) throws Exception {
    generateKeysIfNotExist();
  }

  public static void generateKeysIfNotExist() {
    generateRsaKeysIfNotExist();
  }

  /**
   * Generate RSA key pair for JWT signing/verification
   */
  private static void generateRsaKeysIfNotExist() {
    try {
      // Determine where to create keys based on environment
      Path keyDir = getKeyDirectory();
      Path privatePem = keyDir.resolve("private_key_pkcs8.pem");
      Path publicPem = keyDir.resolve("public_key.pem");

      if (Files.exists(privatePem) && Files.exists(publicPem)) {
        logger.info("🔑 RSA keys already exist, skipping generation");
        return;
      }

      logger.info("🔑 RSA keys not found, generating new keys in: {}", keyDir.toAbsolutePath());

      createDirIfNotExists(keyDir);

      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048);
      KeyPair pair = keyGen.generateKeyPair();

      writePem(privatePem, "PRIVATE KEY", pair.getPrivate().getEncoded());
      writePem(publicPem, "PUBLIC KEY", pair.getPublic().getEncoded());

      logger.info("✅ RSA keys generated successfully:");
      logger.info(" - Private key: {}", privatePem.toAbsolutePath());
      logger.info(" - Public key: {}", publicPem.toAbsolutePath());

    } catch (Exception e) {
      logger.error("❌ Failed to generate RSA keys", e);
      throw new RuntimeException("Could not generate RSA keys", e);
    }
  }

  private static Path getKeyDirectory() {
    // Check if running in Docker container
    String javaClassPath = System.getProperty("java.class.path");
    if (javaClassPath != null && javaClassPath.contains("/app.jar")) {
      // Running in Docker - create keys in /tmp and create symlink
      return Paths.get("/tmp/auth-keys");
    } else {
      // Running in development
      return Paths.get("src", "main", "resources", "keys");
    }
  }

  private static void createDirIfNotExists(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
      System.out.println("📂 Created directory: " + dir.toAbsolutePath());
    }
  }

  private static void writePem(Path path, String type, byte[] bytes) throws IOException {
    String pem = "-----BEGIN " + type + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(bytes)
        + "\n-----END " + type + "-----\n";
    Files.writeString(path, pem, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("📝 Wrote " + type + " → " + path);
  }
}
