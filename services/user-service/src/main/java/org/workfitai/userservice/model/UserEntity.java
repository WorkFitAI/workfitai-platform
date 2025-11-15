package org.workfitai.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.validation.validator.ValidUserRole;
import org.workfitai.userservice.validation.validator.ValidUserStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class UserEntity extends AbstractAuditingEntity {
  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "user_id", updatable = false, nullable = false)
  private UUID userId;

  @NotBlank(message = "Full name is required")
  @Pattern(regexp = "^[a-zA-Z\\s]{3,255}$",
      message = "Full name must be at least 3 characters long and can contain letters and spaces only")
  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Email(message = "Email must have right format example@gmail.com")
  @NotBlank(message = "Email is required")
  @Size(max = 255, message = "Email have maximum 255 characters")
  @Column(name = "email", unique = true, nullable = false)
  private String email;

  @NotBlank(message = "Phone number is required")
  @Pattern(regexp = "^(\\+84)?\\d{10}$",
      message = "Phone number must be 10 digits long and can optionally start with a '+84' country code")
  @Column(name = "phone_number", unique = true, nullable = false)
  private String phoneNumber;

  @JsonIgnore
  @Column(name = "password_hash", nullable = false)
  @NotBlank(message = "Password hash is required")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @NotNull(message = "User role must not be null")
  @ValidUserRole
  @Column(name = "userRole", nullable = false)
  private EUserRole userRole;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @NotNull(message = "User status must not be null")
  @ValidUserStatus
  @Column(name = "userStatus", nullable = false)
  private EUserStatus userStatus = EUserStatus.PENDING;

  @Column(name = "last_login", nullable = true)
  private Instant lastLogin = null;

  @Override
  public UUID getId() {
    return this.userId;
  }
}

