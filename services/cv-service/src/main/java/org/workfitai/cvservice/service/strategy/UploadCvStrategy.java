package org.workfitai.cvservice.service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.errors.BusinessException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.PdfComplexityDetector;
import org.workfitai.cvservice.service.nlp.layout.GridProjectionTextExtractor;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
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

    /**
     * Job-title / role words. A short line containing any of these is virtually
     * always a job title or role name (real CV content), never a section header —
     * but empirically scores above the semantic-match threshold against "skills"
     * or "experience" anchors often enough to corrupt currentSection if not
     * blocked here first (e.g. "Junior Software Engineer" → falsely "skills").
     */
    private static final Set<String> JOB_TITLE_WORDS = Set.of(
            "engineer", "developer", "manager", "analyst", "specialist", "intern",
            "consultant", "designer", "architect", "officer", "director",
            "coordinator", "administrator", "scientist", "lead", "founder",
            "president", "executive", "associate", "assistant", "supervisor");

    private static boolean looksLikeJobTitle(String cleanLine) {
        for (String word : cleanLine.split("\\s+")) {
            if (JOB_TITLE_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private final SemanticMatchingService semanticMatchingService;
    private final DynamicSkillExtractionService skillExtractor;
    private final FileService fileService;
    private final PdfComplexityDetector pdfComplexityDetector;

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
            // Carry raw text (in-memory, @Transient) for the async Ollama section
            // re-extraction step triggered after the CV is saved.
            cv.setRawText(text);
            return cv;

        } catch (BusinessException e) {
            // Preserve the specific 4xx (e.g. InvalidDataException from the OCR
            // pre-check) instead of flattening it into a generic 500 below.
            throw e;
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
     * Raw extracted PDF text plus the structured parse of it — lets snapshot
     * creation keep the raw text (for async Ollama re-extraction) without
     * re-reading the PDF stream a second time.
     */
    public record ParsedCvResult(String rawText, ParsedCvData parsed) {
    }

    /**
     * Parse a PDF MultipartFile into structured CV sections. Used by snapshot
     * creation.
     */
    public ParsedCvData parsePdfFile(MultipartFile file) throws IOException {
        return parsePdfFile(file.getInputStream());
    }

    /**
     * Like {@link #parsePdfFile(MultipartFile)} but also returns the raw extracted
     * text, so the caller can hand it to the async Ollama section-enrichment step.
     */
    public ParsedCvResult parsePdfFileWithText(MultipartFile file) throws IOException {
        String rawText = extractPdfText(file.getInputStream());
        return new ParsedCvResult(rawText, parseCvText(rawText));
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

    /**
     * Extracts raw text only (no section parsing) from a PDF stream. Used by the
     * startup Ollama backfill runner: it only needs the raw text to send to
     * recommendation-engine — the CV's existing (heuristic) sections are already
     * stored, so re-running heuristic parsing here would be wasted work.
     */
    public String extractRawText(InputStream pdfStream) throws IOException {
        return extractPdfText(pdfStream);
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        return extractPdfText(file.getInputStream());
    }

    private String extractPdfText(InputStream pdfStream) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfStream)) {
            PdfComplexityDetector.Assessment assessment = pdfComplexityDetector.assess(doc);
            if (assessment.needsOcr()) {
                log.warn("PDF rejected before parsing, reason={}", assessment.reason());
                throw new InvalidDataException(
                        "This CV appears to be a scanned image without extractable text — please upload a text-based PDF",
                        HttpStatus.BAD_REQUEST);
            }
            return new GridProjectionTextExtractor().extract(doc);
        }
    }

    private boolean isContactInfo(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("@") && lower.contains("."))
            return true;
        if (lower.contains("github.com") || lower.contains("linkedin.com") || lower.startsWith("http")
                || lower.contains("www."))
            return true;

        // Phone numbers are short lines that are *mostly* digits (e.g. "0901 234 567",
        // "+1 (415) 555-0199"). Require the line itself to be short too, otherwise a
        // sentence that happens to contain several numbers (a budget figure, a year,
        // a metric like "reduced cost by $80,000 in 2023") gets wrongly dropped as PII.
        String numbersOnly = line.replaceAll("[^0-9]", "");
        return numbersOnly.length() >= 8 && numbersOnly.length() <= 15 && line.length() <= 25;
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
            // Job titles ("Junior Software Engineer", "Project Manager"...) are real
            // content, never headers — skip header detection entirely for them.
            // Empirically (real embedding model probe) several of these otherwise
            // score above threshold against an unrelated anchor (e.g. "skills"),
            // silently hijacking currentSection mid-document.
            if (!cleanLine.isEmpty() && cleanLine.split("\\s+").length <= 5 && !looksLikeJobTitle(cleanLine)) {
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