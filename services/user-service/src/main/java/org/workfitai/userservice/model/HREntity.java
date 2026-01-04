package org.workfitai.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@PrimaryKeyJoinColumn(name = "user_id")
public class HREntity extends UserEntity {

  @Size(max = 255, message = "Department name must not exceed 255 characters")
  @NotBlank(message = "Department name is required")
  @Column(name = "department", nullable = false)
  private String department;

  /**
   * UUID for internal linking within user-service.
   */
  @NotNull(message = "Company ID must not be null")
  @Column(name = "company_id", nullable = false)
  private UUID companyId;

  /**
   * Mã số thuế (Tax ID) - primary key in job-service Company table.
   * This is what users input during registration and what job-service uses.
   */
  @NotBlank(message = "Company number (tax ID) is required")
  @Column(name = "company_no", nullable = false)
  private String companyNo;

  /**
   * Company name from registration - sent to job-service for synchronization.
   */
  @Size(max = 255, message = "Company name must not exceed 255 characters")
  @Column(name = "company_name", nullable = true)
  private String companyName;

  @Size(max = 255, message = "Address must not exceed 255 characters")
  @NotBlank(message = "Address is required")
  @Column(name = "address", nullable = false)
  private String address;

  /**
   * Người duyệt (admin hoặc hệ thống), có thể null nếu chưa duyệt.
   */
  @Column(name = "approved_by")
  private String approvedBy;

  @PastOrPresent(message = "Approval date must be in the past or present")
  @Column(name = "approved_at")
  private Instant approvedAt;

  @Override
  public UUID getId() {
    return super.getId();
  }
}
