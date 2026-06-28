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
import java.util.*;

@Service
@RequiredArgsConstructor
public class UploadCvStrategy implements CvCreationStrategy<ReqCvUploadDTO> {

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
            sections.put("summary", Objects.requireNonNullElse(parsedData.getSummary(), Collections.emptyList()));

            cv.setSections(sections);
            List<String> summary = parsedData.getSummary();
            cv.setSummary(summary != null ? String.join("\n", summary) : "");
            return cv;

        } catch (Exception e) {
            throw new RuntimeException("Upload CV file failed", e);
        }
    }

    /**
     * Parse a PDF MultipartFile into structured CV sections. Used by snapshot
     * creation.
     */
    public ParsedCvData parsePdfFile(MultipartFile file) throws IOException {
        String rawText = extractPdfText(file);
        return parseCvText(rawText);
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
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
        sectionData.put("experience", new ArrayList<>());
        sectionData.put("skills", new ArrayList<>());
        sectionData.put("education", new ArrayList<>());

        List<String> lines = Arrays.stream(rawText.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        // Mặc định bỏ qua các dòng thông tin cá nhân trên cùng cho đến khi gặp section
        // đầu tiên
        String currentSection = "ignored";

        for (String line : lines) {
            // 1. Loại bỏ PII (Email, Phone, Links)
            if (isContactInfo(line)) {
                continue;
            }

            String cleanLine = line.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();

            // 2. Chặn tiêu đề bằng Semantic AI (nếu dòng ngắn <= 4 từ)
            if (!cleanLine.isEmpty() && cleanLine.split("\\s+").length <= 4) {
                String mappedSection = semanticMatchingService.mapToCoreField(cleanLine);
                // Chỉ đổi section khi khớp đủ tin cậy. Nếu không khớp (ignored),
                // dòng ngắn này (tiêu đề công việc, ngày tháng, bullet ngắn...)
                // vẫn thuộc về section hiện tại, không được reset về ignored.
                if (!"ignored".equals(mappedSection)) {
                    currentSection = mappedSection;
                    continue;
                }
            }

            // 3. Gom Data vào 4 trụ cột
            if (!"ignored".equals(currentSection) && sectionData.containsKey(currentSection)) {
                sectionData.get(currentSection).add(line);
            }
        }

        // 4. Vét cạn Kỹ năng (Moi từ trong Experience và Summary)
        String textToMine = String.join(" ", sectionData.get("experience")) +
                " " +
                String.join(" ", sectionData.get("summary"));

        List<String> minedSkills = skillExtractor.extractSkills(textToMine);

        // Merge kỹ năng móc được vào mảng kỹ năng chính (nếu có)
        Set<String> finalSkills = new HashSet<>(sectionData.get("skills"));
        finalSkills.addAll(minedSkills);

        // 5. Build DTO trả về
        ParsedCvData data = new ParsedCvData();
        data.setSummary(sectionData.get("summary"));
        data.setExperience(sectionData.get("experience"));
        data.setEducation(sectionData.get("education"));
        data.setSkills(new ArrayList<>(finalSkills));

        return data;
    }
}
