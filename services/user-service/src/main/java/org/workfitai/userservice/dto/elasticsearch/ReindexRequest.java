package org.workfitai.userservice.dto.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for triggering bulk reindex of users to Elasticsearch
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReindexRequest {

    /**
     * Run reindex asynchronously (default: true)
     */
    @Builder.Default
    private Boolean async = true;

    /**
     * Batch size for bulk indexing (default: 1000)
     */
    @Builder.Default
    private Integer batchSize = 1000;
}
