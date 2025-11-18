package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.authservice.config.RsaKeyProperties;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
public class PublicKeyController {

  private final RsaKeyProperties rsaKeys;

  @GetMapping("/public")
  public Map<String, String> getPublicKey() {
    String encoded = Base64.getEncoder().encodeToString(rsaKeys.publicKey().getEncoded());
    return Map.of(
        "alg", "RS256",
        "type", "RSA",
        "publicKey", encoded
    );
  }
}
