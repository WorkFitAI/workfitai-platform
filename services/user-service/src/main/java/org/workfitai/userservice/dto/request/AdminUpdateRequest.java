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
public class AdminUpdateRequest {

  @Size(min = 3, max = 255, message = "Full name must be between 3–255 characters")
  private String fullName;

  @Email(message = "Invalid email format")
  private String email;

  @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Invalid phone number format")
  private String phoneNumber;

  // nếu admin có thêm quyền đổi status user thì có thể thêm:
  private String userStatus;
}
