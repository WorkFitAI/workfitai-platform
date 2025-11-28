package org.workfitai.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.workfitai.userservice.enums.ESkillLevel;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_profile_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CandidateProfileSkillEntity extends AbstractAuditingEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @NotNull(message = "Skill ID must not be null")
  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @NotNull(message = "Skill level must not be null")
  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private ESkillLevel level;

  @Min(value = 0, message = "Years of experience must be >= 0")
  @Max(value = 50, message = "Years of experience must be <= 50")
  @Column(name = "years_of_experience", nullable = false)
  private int yearsOfExperience;

  @PastOrPresent(message = "Added date must be in the past or present")
  @Column(name = "added_at", nullable = false, updatable = false)
  private Instant addedAt = Instant.now();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "candidate_id", referencedColumnName = "user_id", nullable = false)
  private CandidateEntity candidate;

  @Override
  public UUID getId() {
    return this.id;
  }
}
