package org.workfitai.cvservice.service.strategy;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UploadCvStrategy implements CvCreationStrategy<ReqCvUploadDTO> {

    /**
     * Exact-phrase lookup for standard CV section headers.
     * Matched against the fully cleaned (lower-case, non-alpha stripped) line
     * BEFORE
     * falling through to the slower semantic embedding model.
     */
    private static final Map<String, String> SECTION_HEADER_MAP = Map.ofEntries(
            Map.entry("objective", "objective"),
            Map.entry("career objective", "objective"),
            Map.entry("education", "education"),
            Map.entry("academic background", "education"),
            Map.entry("academic qualifications", "education"),
            Map.entry("experience", "experience"),
            Map.entry("work experience", "experience"),
            Map.entry("professional experience", "experience"),
            Map.entry("employment history", "experience"),
            Map.entry("work history", "experience"),
            Map.entry("project", "projects"),
            Map.entry("projects", "projects"),
            Map.entry("personal projects", "projects"),
            Map.entry("side projects", "projects"),
            Map.entry("skills", "skills"),
            Map.entry("technical skills", "skills"),
            Map.entry("core competencies", "skills"),
            Map.entry("technologies", "skills"),
            Map.entry("languages", "languages"),
            Map.entry("language skills", "languages"),
            Map.entry("certifications", "certifications"),
            Map.entry("certification", "certifications"),
            Map.entry("certificates", "certifications"),
            Map.entry("awards", "certifications"),
            Map.entry("achievements", "certifications"),
            Map.entry("summary", "summary"),
            Map.entry("professional summary", "summary"),
            Map.entry("about me", "summary"));

    private final SemanticMatchingService semanticMatchingService;
    private final DynamicSkillExtractionService skillExtractor;
    private final FileService fileService;

    @Override
    public CV createCv(ReqCvUploadDTO dto) {
        try {
            // Upload file
            String objectName = fileService.uploadCV(dto.getFile());
            String fileUrl = fileService.generateFileUrl(objectName);

            dto.setPdfUrl(fileUrl);
            dto.setObjectName(objectName);

            // Đọc PDF
            String text = extractPdfText(dto.getFile());

            // Parse text → sections
            ParsedCvData parsedData = parseCvText(text);

            CV cv = CVMapper.INSTANCE.toEntityFromUpload(dto);

            Map<String, Object> sections = new HashMap<>();
            sections.put("skills", Objects.requireNonNullElse(parsedData.getSkills(), Collections.emptyList()));
            sections.put("experience", Objects.requireNonNullElse(parsedData.getExperience(), Collections.emptyList()));
            sections.put("education", Objects.requireNonNullElse(parsedData.getEducation(), Collections.emptyList()));
            sections.put("projects", Objects.requireNonNullElse(parsedData.getProjects(), Collections.emptyList()));
            sections.put("languages", Objects.requireNonNullElse(parsedData.getLanguages(), Collections.emptyList()));
            sections.put("certifications",
                    Objects.requireNonNullElse(parsedData.getCertifications(), Collections.emptyList()));
            sections.put("objective", Objects.requireNonNullElse(parsedData.getObjective(), Collections.emptyList()));
            sections.put("summary", Objects.requireNonNullElse(parsedData.getSummary(), Collections.emptyList()));

            cv.setSections(sections);
            cv.setSummary(buildSummaryText(parsedData));
            return cv;

        } catch (Exception e) {
            throw new RuntimeException("Upload CV file failed", e);
        }
    }

    /**
     * Merges summary and objective lines into a single string for the CV.summary
     * field.
     */
    private String buildSummaryText(ParsedCvData data) {
        List<String> lines = new java.util.ArrayList<>();
        if (data.getSummary() != null)
            lines.addAll(data.getSummary());
        if (data.getObjective() != null)
            lines.addAll(data.getObjective());
        return String.join("\n", lines);
    }

    /**
     * Parse a PDF MultipartFile into structured CV sections. Used by snapshot
     * creation.
     */
    public ParsedCvData parsePdfFile(MultipartFile file) throws IOException {
        return parsePdfFile(file.getInputStream());
    }

    /**
     * Parse a PDF from a raw stream into structured CV sections. Used by snapshot
     * reconciliation, where the PDF bytes come from re-downloading {@code cvFileUrl}
     * instead of a fresh multipart upload.
     */
    public ParsedCvData parsePdfFile(InputStream pdfStream) throws IOException {
        String rawText = extractPdfText(pdfStream);
        return parseCvText(rawText);
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        return extractPdfText(file.getInputStream());
    }

    private String extractPdfText(InputStream pdfStream) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private boolean isContactInfo(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("@") && lower.contains("."))
            return true;
        if (lower.contains("github.com") || lower.contains("linkedin.com") || lower.startsWith("http")
                || lower.contains("www."))
            return true;

        String numbersOnly = line.replaceAll("[^0-9]", "");
        return numbersOnly.length() >= 8 && numbersOnly.length() <= 15;
    }

    private ParsedCvData parseCvText(String rawText) {
        Map<String, List<String>> sectionData = new HashMap<>();
        sectionData.put("summary", new ArrayList<>());
        sectionData.put("objective", new ArrayList<>());
        sectionData.put("experience", new ArrayList<>());
        sectionData.put("skills", new ArrayList<>());
        sectionData.put("education", new ArrayList<>());
        sectionData.put("projects", new ArrayList<>());
        sectionData.put("languages", new ArrayList<>());
        sectionData.put("certifications", new ArrayList<>());

        List<String> lines = Arrays.stream(rawText.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        String currentSection = "ignored";

        for (String line : lines) {
            // 1. Loại bỏ PII (Email, Phone, Links)
            if (isContactInfo(line)) {
                continue;
            }

            String cleanLine = line.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();

            // 2. Detect section headers (short lines ≤ 5 words)
            if (!cleanLine.isEmpty() && cleanLine.split("\\s+").length <= 5) {
                // Fast-path: exact phrase lookup for standard CV headers
                String exactMatch = SECTION_HEADER_MAP.get(cleanLine);
                if (exactMatch != null) {
                    currentSection = exactMatch;
                    continue;
                }
                // Semantic fallback for non-standard but semantically similar headers
                String mappedSection = semanticMatchingService.mapToCoreField(cleanLine);

                if (!"ignored".equals(mappedSection)) {
                    currentSection = mappedSection;
                    continue;
                }
            }

            // 3. Accumulate data into the current section bucket
            if (!"ignored".equals(currentSection) && sectionData.containsKey(currentSection)) {
                sectionData.get(currentSection).add(line);
            }
        }

        // 4. Mine skills from experience, projects, summary, and objective text
        String textToMine = String.join(" ", sectionData.get("experience"))
                + " " + String.join(" ", sectionData.get("projects"))
                + " " + String.join(" ", sectionData.get("summary"))
                + " " + String.join(" ", sectionData.get("objective"));

        List<String> minedSkills = skillExtractor.extractSkills(textToMine);

        Set<String> finalSkills = new HashSet<>(sectionData.get("skills"));
        finalSkills.addAll(minedSkills);

        // 5. Build DTO
        ParsedCvData data = new ParsedCvData();
        data.setSummary(sectionData.get("summary"));
        data.setObjective(sectionData.get("objective"));
        data.setExperience(sectionData.get("experience"));
        data.setEducation(sectionData.get("education"));
        data.setProjects(sectionData.get("projects"));
        data.setLanguages(sectionData.get("languages"));
        data.setCertifications(sectionData.get("certifications"));
        data.setSkills(new ArrayList<>(finalSkills));

        return data;
    }
}