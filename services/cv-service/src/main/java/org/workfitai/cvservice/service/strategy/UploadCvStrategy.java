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
import org.workfitai.cvservice.service.shared.FileService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UploadCvStrategy implements CvCreationStrategy<ReqCvUploadDTO> {

    private static final Map<String, List<String>> SECTION_ALIASES = Map.of(
            "skills", List.of("Skills", "Technical Skills", "Professional Skills", "Key Skills"),
            "projects", List.of("Projects", "Project", "Personal Projects", "Work Projects"),
            "experience", List.of("Experience", "Work Experience", "Professional Experience"),
            "education", List.of("Education", "Academic Background", "Education & Training"),
            "languages", List.of("Languages", "Language Skills", "Languages Known"),
            "objective", List.of("Objective", "Career Objective", "Professional Summary", "Summary")
    );
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
            sections.put("skills", parsedData.getSkills());
            sections.put("projects", parsedData.getProjects());
            sections.put("experience", parsedData.getExperience());
            sections.put("education", parsedData.getEducation());
            sections.put("languages", parsedData.getLanguages());

            cv.setSections(sections);
            cv.setHeadline(parsedData.getHeadline());
            cv.setSummary(String.join("\n", parsedData.getSummary()));
            return cv;

        } catch (Exception e) {
            throw new RuntimeException("Upload CV file failed", e);
        }
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private ParsedCvData parseCvText(String text) {
        ParsedCvData data = new ParsedCvData();

        List<String> lines = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        data.setSummary(extractSection(lines, "Objective"));
        data.setSkills(extractSection(lines, "Skills"));
        data.setProjects(extractSection(lines, "Projects"));
        data.setExperience(extractSection(lines, "Experience"));
        data.setEducation(extractSection(lines, "Education"));
        data.setLanguages(extractSection(lines, "Languages"));

        return data;
    }

    private List<String> extractSection(List<String> lines, String sectionKey) {
        List<String> result = new ArrayList<>();
        boolean inSection = false;
        List<String> aliases = SECTION_ALIASES.getOrDefault(sectionKey.toLowerCase(), List.of(sectionKey));

        Set<String> allAliases = SECTION_ALIASES.values().stream().flatMap(List::stream).collect(Collectors.toSet());

        for (String line : lines) {
            // Bắt đầu section nếu match alias
            if (aliases.stream().anyMatch(a -> line.equalsIgnoreCase(a))) {
                inSection = true;
                continue;
            }

            // Dừng khi gặp alias section khác
            if (inSection && allAliases.stream().anyMatch(a -> line.equalsIgnoreCase(a))) {
                break;
            }

            if (inSection) {
                result.add(line);
            }
        }
        return result;
    }
}

