package org.workfitai.authservice.utils;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;

public class KeyGenerator {
  public static void main(String[] args) throws Exception {
    Path keyDir = Paths.get("src", "main", "resources", "keys");
    createDirIfNotExists(keyDir);

    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair pair = keyGen.generateKeyPair();

    Path privatePem = keyDir.resolve("private_key_pkcs8.pem");
    Path publicPem  = keyDir.resolve("public_key.pem");

    writePem(privatePem, "PRIVATE KEY", pair.getPrivate().getEncoded());
    writePem(publicPem,  "PUBLIC KEY",  pair.getPublic().getEncoded());

    System.out.println("✅ Generated:");
    System.out.println(" - " + privatePem.toAbsolutePath());
    System.out.println(" - " + publicPem.toAbsolutePath());
  }

  private static void createDirIfNotExists(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
      System.out.println("📂 Created directory: " + dir.toAbsolutePath());
    }
  }

  private static void writePem(Path path, String type, byte[] bytes) throws IOException {
    String pem = "-----BEGIN " + type + "-----\n"
        + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
        + "\n-----END " + type + "-----\n";
    Files.writeString(path, pem, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("📝 Wrote " + type + " → " + path);
  }
}
