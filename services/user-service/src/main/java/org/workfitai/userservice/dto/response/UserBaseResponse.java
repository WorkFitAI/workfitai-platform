package org.workfitai.userservice.dto.response;

import lombok.*;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBaseResponse {
  private UUID userId;
  private String fullName;
  private String email;
  private String phoneNumber;

  private EUserRole userRole;
  private EUserStatus userStatus;
  private Instant lastLogin;

  // auditing info
  private String createdBy;
  private Instant createdDate;
  private String lastModifiedBy;
  private Instant lastModifiedDate;
  private boolean isDeleted;
}
