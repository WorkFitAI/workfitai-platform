package org.workfitai.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HRUpdateRequest {

  // ===== USER FIELDS =====
  @Size(min = 3, max = 255, message = "Full name must be between 3–255 characters")
  private String fullName;

  @Email(message = "Invalid email format")
  private String email;

  @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Invalid phone number format")
  private String phoneNumber;

  // ===== HR FIELDS =====
  private String department;
  private String address;

  // Nếu HR có thể đổi công ty thì giữ; không thì bỏ
  private String companyId;
}
