package org.workfitai.jobservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill extends AbstractAuditingEntity<UUID> {
    @Id
    private UUID skillId;

    private String name;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "skills")
    private List<Job> jobs;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "skills")
    private List<Subscriber> subscribers;

    @Override
    public UUID getId() {
        return this.skillId;
    }
}
