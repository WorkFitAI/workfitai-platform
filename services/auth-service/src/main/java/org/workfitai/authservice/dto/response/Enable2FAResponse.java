package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enable2FAResponse {
    private String method;
    private String secret;
    private String qrCodeUrl;
    private List<String> backupCodes;
    private String message;
}
