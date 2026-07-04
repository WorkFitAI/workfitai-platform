package org.workfitai.cvservice.service.nlp;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConfigurationProperties(prefix = "cv-parser")
public class SemanticMatchingService {

    @Getter
    @Setter
    private Map<String, String> anchors;

    @Getter
    @Setter
    private double threshold;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final Map<String, Embedding> coreEmbeddings = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Khởi tạo Semantic CV Parser...");
        if (anchors == null || anchors.isEmpty()) {
            log.warn("cv-parser.anchors not configured — SemanticMatchingService will not embed any anchors.");
            return;
        }
        for (Map.Entry<String, String> entry : anchors.entrySet()) {
            coreEmbeddings.put(entry.getKey(), embeddingModel.embed(entry.getValue()).content());
        }
        log.info("Nạp xong {} trụ cột ngữ nghĩa. Threshold = {}", coreEmbeddings.size(), threshold);
    }

    public String mapToCoreField(String foundHeader) {
        Embedding headerEmbedding = embeddingModel.embed(foundHeader.toLowerCase()).content();
        String bestMatch = "ignored";
        double highestScore = -1.0;

        for (Map.Entry<String, Embedding> entry : coreEmbeddings.entrySet()) {
            double score = CosineSimilarity.between(headerEmbedding, entry.getValue());
            if (score > highestScore) {
                highestScore = score;
                bestMatch = entry.getKey();
            }
        }

        if (highestScore < threshold) {
            return "ignored";
        }
        return bestMatch;
    }
}
