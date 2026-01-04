package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("roles")
public class Role {
    @Id
    String id;
    @Indexed(unique=true)
    String name;        // e.g. "ADMIN"
    String description;
    Set<String> permissions;                  // holds Permission.name values
}