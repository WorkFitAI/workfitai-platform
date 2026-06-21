package org.workfitai.cvservice.service.impl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.constant.ErrorConst;
import org.workfitai.cvservice.dto.kafka.CvUpdatedEvent;
import org.workfitai.cvservice.dto.kafka.NotificationEvent;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.errors.ResourceNotFoundException;
import org.workfitai.cvservice.messaging.CvEventProducer;
import org.workfitai.cvservice.messaging.NotificationProducer;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.dto.response.CvDataResponse;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.model.enums.TemplateType;
import org.workfitai.cvservice.model.mapper.CVMapper;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.factory.CvCreationFactory;
import org.workfitai.cvservice.service.iCVService;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.CvCreationStrategy;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;
import org.workfitai.cvservice.utils.CvQueryBuilder;
import org.workfitai.cvservice.utils.PaginationUtils;
import org.workfitai.cvservice.validation.FileValidator;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CVService implements iCVService {
    private final CVRepository repository;
    private final MongoTemplate mongoTemplate;
    private final CvCreationFactory cvCreationFactory;
    private final FileService fileService;
    private final NotificationProducer notificationProducer;
    private final CvEventProducer cvEventProducer;
    private final UploadCvStrategy uploadCvStrategy;

    // ---------------- CREATE ----------------
    @Override
    @Transactional
    public <T> ResCvDTO createCv(String type, T dto) throws InvalidDataException {
        validateUploadFile(type, dto);

        CvCreationStrategy<T> strategy = (CvCreationStrategy<T>) cvCreationFactory.getStrategy(type);
        CV cv = strategy.createCv(dto);

        cv.setBelongTo(getCurrentUsername());

        CV saved = repository.save(cv);

        ResCvDTO created = CVMapper.INSTANCE.toResDTO(saved);

        log.info("Created CV with ID: {} with {}", created.getCvId(), created.isExist());

        // Send notification after CV upload/creation
        sendCvUploadNotification(saved, type);

        return created;
    }

    /**
     * Send email notification after CV upload/parse
     * Notification service will check user's notification and privacy settings
     */
    private void sendCvUploadNotification(CV cv, String type) {
        try {
            String username = getCurrentUsername();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("cvId", cv.getCvId());
            metadata.put("fileName", cv.getObjectName() != null ? cv.getObjectName() : "CV");
            metadata.put("uploadedAt",
                    cv.getCreatedAt() != null ? cv.getCreatedAt().toString() : Instant.now().toString());
            metadata.put("belongTo", username);
            metadata.put("type", type);
            metadata.put("fileUrl", cv.getPdfUrl() != null ? cv.getPdfUrl() : "");

            NotificationEvent event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("CV_UPLOADED")
                    .timestamp(Instant.now())
                    .recipientUserId(username) // notification-service will fetch user email
                    .recipientRole("CANDIDATE")
                    .subject("CV Uploaded Successfully")
                    .content("Your CV \"" + (cv.getObjectName() != null ? cv.getObjectName() : "CV")
                            + "\" has been uploaded successfully.")
                    .templateType("cv-upload-success")
                    .notificationType("cv_uploaded") // Add notification type
                    .sendEmail(true)
                    .createInAppNotification(true) // ✅ Enable in-app notification
                    .referenceId(cv.getCvId())
                    .referenceType("CV")
                    .metadata(metadata)
                    .build();

            notificationProducer.send(event);
            log.info("Sent CV upload notification for CV: {} to user: {}", cv.getCvId(), username);
        } catch (Exception e) {
            log.error("Failed to send CV upload notification for CV: {}", cv.getCvId(), e);
        }
    }

    // ---------------- GET BY ID ----------------
    @Override
    public ResCvDTO getById(String cvId) {
        CV cv = repository.findById(cvId)
                .filter(CV::isExist)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConst.CV_NOT_FOUND));

        return CVMapper.INSTANCE.toResDTO(cv);
    }

    // ---------------- GET WITHOUT FILTER ----------------
    @Override
    public ResultPaginationDTO<ResCvDTO> getCVByBelongTo(String username, int page, int size) {
        return getCVByBelongToWithFilter(username, Map.of(), page, size);
    }

    // ---------------- GET WITH FILTER ----------------
    @Override
    public ResultPaginationDTO<ResCvDTO> getCVByBelongToWithFilter(
            String username,
            Map<String, Object> filters,
            int page,
            int size) {

        Query query = CvQueryBuilder.build(username, filters);

        long total = mongoTemplate.count(query, CV.class);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        query.with(pageable);

        List<ResCvDTO> results = mongoTemplate.find(query, CV.class)
                .stream()
                .map(CVMapper.INSTANCE::toResDTO)
                .toList();

        return PaginationUtils.buildResult(results, total, page, size);
    }

    // ---------------- UPDATE ----------------
    @Override
    public ResCvDTO update(String cvId, @Valid ReqCvDTO req) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(ErrorConst.CV_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!cv.isExist()) {
            throw new CVConflictException(ErrorConst.CV_CONFLICT_DATA);
        }

        CVMapper.INSTANCE.updateFromDto(req, cv);
        CV saved = repository.save(cv);

        publishCvUpdatedEvent(saved);

        return CVMapper.INSTANCE.toResDTO(saved);
    }

    private void publishCvUpdatedEvent(CV cv) {
        try {
            CvUpdatedEvent event = CvUpdatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .username(cv.getBelongTo())
                    .cvId(cv.getCvId())
                    .resumeSummary(cv.getSummary() != null ? cv.getSummary() : "")
                    .resumeExperience(extractSection(cv.getSections(), "experience"))
                    .resumeSkills(extractSection(cv.getSections(), "skills"))
                    .resumeEducation(extractSection(cv.getSections(), "education"))
                    .updatedAt(cv.getUpdatedAt() != null ? cv.getUpdatedAt() : Instant.now())
                    .build();
            cvEventProducer.sendCvUpdated(event);
        } catch (Exception e) {
            log.error("Failed to publish cv.updated event for CV: {}", cv.getCvId(), e);
        }
    }

    // ---------------- SOFT DELETE ----------------
    @Override
    public void delete(String cvId) throws CVConflictException, InvalidDataException {
        CV cv = repository.findById(cvId)
                .orElseThrow(() -> new InvalidDataException(ErrorConst.CV_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!cv.isExist()) {
            throw new CVConflictException(ErrorConst.CV_CONFLICT_DATA);
        }

        cv.setExist(false);
        repository.save(cv);
    }

    // ---------------- DOWNLOAD ----------------
    @Override
    public InputStream downloadCV(String objectName) {
        if (!objectName.matches(CVConst.PDF_FILE_PATTERN)) {
            throw new InvalidDataException(ErrorConst.CV_INVALID_FILE, HttpStatus.BAD_REQUEST);
        }

        try {
            return fileService.downloadCV(objectName);
        } catch (Exception e) {
            throw new InvalidDataException("Cannot read file: " + objectName, HttpStatus.BAD_REQUEST);
        }
    }

    // ---------------- PRIVATE UTILS ----------------
    private <T> void validateUploadFile(String type, T dto) {
        if ("upload".equalsIgnoreCase(type) && dto instanceof ReqCvUploadDTO upload) {
            FileValidator.validate(upload.getFile());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractSection(Map<String, Object> sections, String key) {
        if (sections == null || !sections.containsKey(key)) {
            return "";
        }
        Object value = sections.get(key);
        if (value instanceof List<?> list) {
            return String.join("\n", (List<String>) list);
        }
        return value != null ? value.toString() : "";
    }

    @Override
    public List<CvDataResponse> getCvDataBatch(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        // One query, then group by belongTo and keep the latest CV per user
        return repository.findByBelongToInAndIsExistTrue(usernames)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CV::getBelongTo,
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.maxBy(
                                        java.util.Comparator.comparing(cv ->
                                                cv.getUpdatedAt() != null ? cv.getUpdatedAt() : cv.getCreatedAt())),
                                opt -> opt.map(cv -> CvDataResponse.builder()
                                        .username(cv.getBelongTo())
                                        .resumeSummary(cv.getSummary() != null ? cv.getSummary() : "")
                                        .resumeExperience(extractSection(cv.getSections(), "experience"))
                                        .resumeSkills(extractSection(cv.getSections(), "skills"))
                                        .resumeEducation(extractSection(cv.getSections(), "education"))
                                        .build()).orElse(null))))
                .values()
                .stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }

    // ---------------- APPLICATION SNAPSHOT ----------------
    @Override
    public CvSnapshotResponse createApplicationSnapshot(
            String username,
            String applicationId,
            String jobName,
            org.springframework.web.multipart.MultipartFile file) {

        try {
            // Persist the PDF to MinIO so it can be downloaded later — named after the job
            // applied to instead of the candidate's original filename.
            String objectName = fileService.uploadCV(file, jobName);
            String fileUrl = fileService.generateFileUrl(objectName);

            // Reuse existing PDF → sections logic from UploadCvStrategy
            var parsed = uploadCvStrategy.parsePdfFile(file);

            // Build sections map
            Map<String, Object> sections = new HashMap<>();
            sections.put("skills",        parsed.getSkills());
            sections.put("projects",      parsed.getProjects());
            sections.put("experience",    parsed.getExperience());
            sections.put("education",     parsed.getEducation());
            sections.put("languages",     parsed.getLanguages());
            sections.put("objective",     parsed.getObjective());
            sections.put("certifications",parsed.getCertifications());

            String summary = String.join("\n",
                    parsed.getSummary() != null ? parsed.getSummary() : List.of());

            // Build snapshot CV entity — not a regular user CV (isExist kept true for queryability)
            CV snapshot = new CV();
            snapshot.setBelongTo(username);
            snapshot.setApplicationId(applicationId);
            snapshot.setTemplateType(TemplateType.UPLOAD);   // reuse existing enum value
            snapshot.setObjectName(objectName);
            snapshot.setPdfUrl(fileUrl);
            snapshot.setSections(sections);
            snapshot.setSummary(summary);
            snapshot.setHeadline(parsed.getHeadline());
            snapshot.setExist(true);

            CV saved = repository.save(snapshot);

            log.info("CV snapshot created: cvId={} applicationId={} username={}",
                    saved.getCvId(), applicationId, username);

            return CvSnapshotResponse.builder()
                    .cvId(saved.getCvId())
                    .summary(summary)
                    .experience(extractSection(saved.getSections(), "experience"))
                    .skills(extractSection(saved.getSections(), "skills"))
                    .education(extractSection(saved.getSections(), "education"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create CV snapshot for applicationId={} username={}: {}",
                    applicationId, username, e.getMessage(), e);
            throw new RuntimeException("CV snapshot creation failed", e);
        }
    }

    // ---------------- BATCH SNAPSHOT BY SNAPSHOT IDS ----------------
    // Callers pass CV document IDs (cvSnapshotId from Application), so we query by _id, not applicationId.
    @Override
    public List<CvSnapshotResponse> getCvSnapshotsByApplicationIds(List<String> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return List.of();
        }
        List<CV> found = new java.util.ArrayList<>();
        repository.findAllById(snapshotIds).forEach(found::add);
        return found.stream()
                .map(cv -> CvSnapshotResponse.builder()
                        .cvId(cv.getCvId())
                        .summary(cv.getSummary() != null ? cv.getSummary() : "")
                        .experience(extractSection(cv.getSections(), "experience"))
                        .skills(extractSection(cv.getSections(), "skills"))
                        .education(extractSection(cv.getSections(), "education"))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((Jwt) authentication.getPrincipal()).getClaimAsString("sub");
    }
}
