package org.workfitai.userservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "admins")
@NoArgsConstructor
@SuperBuilder
@PrimaryKeyJoinColumn(name = "user_id")
public class AdminEntity extends UserEntity {
}
