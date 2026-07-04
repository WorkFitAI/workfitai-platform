package org.workfitai.cvservice.service.nlp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Trie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.client.JobServiceSkillClient;
import org.workfitai.cvservice.client.dto.JobServiceApiResponseDTO;
import org.workfitai.cvservice.client.dto.SkillDTO;
import org.workfitai.cvservice.client.dto.SkillPageResultDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSkillExtractionService {

    // Last-resort seed used only if job-service has never been reachable.
    private static final List<String> FALLBACK_SKILLS = List.of(
            "java", "spring boot", "react", "next.js", "kafka", "redis",
            "postgresql", "docker", "elasticsearch", "nestjs", "mongodb", "typescript");

    private static final int PAGE_SIZE = 200;

    private final JobServiceSkillClient jobServiceSkillClient;

    private volatile Trie trie;

    @PostConstruct
    public void init() {
        refreshDictionary();
    }

    @Scheduled(fixedDelayString = "${cv-parser.skill-refresh-interval-ms:3600000}")
    public void refreshDictionary() {
        log.info("Đang nạp từ điển kỹ năng từ job-service...");
        try {
            List<String> skillNames = fetchAllSkillNames();
            if (skillNames.isEmpty()) {
                log.warn("job-service trả về danh sách kỹ năng rỗng, giữ nguyên từ điển hiện tại");
                useFallbackIfNeverLoaded();
                return;
            }
            this.trie = buildTrie(skillNames);
            log.info("Đã nạp thành công {} kỹ năng từ job-service", skillNames.size());
        } catch (Exception e) {
            log.warn("Không thể kết nối job-service để nạp từ điển kỹ năng, giữ nguyên từ điển hiện tại: {}",
                    e.getMessage());
            useFallbackIfNeverLoaded();
        }
    }

    private void useFallbackIfNeverLoaded() {
        if (this.trie == null) {
            log.warn("Chưa từng nạp được từ điển kỹ năng, dùng danh sách dự phòng ({} mục)", FALLBACK_SKILLS.size());
            this.trie = buildTrie(FALLBACK_SKILLS);
        }
    }

    private List<String> fetchAllSkillNames() {
        List<String> names = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            JobServiceApiResponseDTO<SkillPageResultDTO> response = jobServiceSkillClient.getAllSkills(page,
                    PAGE_SIZE);
            SkillPageResultDTO data = response == null ? null : response.getData();
            if (data == null || data.getResult() == null) {
                break;
            }
            for (SkillDTO skill : data.getResult()) {
                if (skill.getName() != null && !skill.getName().isBlank()) {
                    names.add(skill.getName());
                }
            }
            totalPages = data.getMeta() != null ? Math.max(data.getMeta().getPages(), 1) : 1;
            page++;
        }
        return names;
    }

    private Trie buildTrie(List<String> skillNames) {
        Trie.TrieBuilder builder = Trie.builder()
                .ignoreCase()
                .ignoreOverlaps()
                .onlyWholeWords();

        for (String skill : skillNames) {
            builder.addKeyword(skill);
        }

        return builder.build();
    }

    public List<String> extractSkills(String text) {
        Set<String> foundSkills = new HashSet<>();
        var emits = trie.parseText(text);
        for (var emit : emits) {
            foundSkills.add(emit.getKeyword());
        }
        return foundSkills.stream().toList();
    }
}
