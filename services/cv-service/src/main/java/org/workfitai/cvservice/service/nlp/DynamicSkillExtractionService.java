package org.workfitai.cvservice.service.nlp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Service;
// import org.workfitai.cvservice.repository.SkillRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSkillExtractionService {

    // private final SkillRepository skillRepository;
    private Trie trie;

    @PostConstruct
    public void init() {
        refreshDictionary();
    }

    public void refreshDictionary() {
        log.info("Đang nạp từ điển kỹ năng từ Database...");

        // Mock data: Thay thế bằng skillRepository.findAllSkillNames() trong thực tế
        List<String> allSkillsFromDb = List.of(
                "java", "spring boot", "react", "next.js", "kafka", "redis",
                "postgresql", "docker", "elasticsearch", "nestjs", "mongodb", "typescript");

        Trie.TrieBuilder builder = Trie.builder()
                .ignoreCase()
                .ignoreOverlaps()
                .onlyWholeWords();

        for (String skill : allSkillsFromDb) {
            builder.addKeyword(skill);
        }

        this.trie = builder.build();
        log.info("Đã nạp thành công {} kỹ năng", allSkillsFromDb.size());
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