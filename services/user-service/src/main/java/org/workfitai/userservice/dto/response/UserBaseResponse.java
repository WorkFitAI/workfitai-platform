package org.workfitai.userservice.dto.response;

import java.time.Instant;
import java.util.UUID;

import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserBaseResponse {
  private UUID userId;
  private String username;
  private String fullName;
  private String email;
  private String phoneNumber;

  private EUserRole userRole;
  private EUserStatus userStatus;

  // auditing info
  private String createdBy;
  private Instant createdDate;
  private String lastModifiedBy;
  private Instant lastModifiedDate;
  private boolean isDeleted;
}
