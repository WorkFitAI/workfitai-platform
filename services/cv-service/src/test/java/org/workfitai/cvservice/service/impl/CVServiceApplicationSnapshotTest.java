package org.workfitai.cvservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.messaging.CvEventProducer;
import org.workfitai.cvservice.messaging.NotificationProducer;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.factory.CvCreationFactory;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

@ExtendWith(MockitoExtension.class)
class CVServiceApplicationSnapshotTest {

    @Mock
    private CVRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private CvCreationFactory cvCreationFactory;
    @Mock
    private FileService fileService;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private CvEventProducer cvEventProducer;
    @Mock
    private UploadCvStrategy uploadCvStrategy;

    @InjectMocks
    private CVService cvService;

    @Test
    void createApplicationSnapshot_uploadsFileAndStoresObjectNameNamedAfterJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[] { 1 });

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-Senior_Backend_Developer.pdf");
        when(fileService.generateFileUrl("uuid-Senior_Backend_Developer.pdf"))
                .thenReturn("http://minio/cvs-files/uuid-Senior_Backend_Developer.pdf");
        when(uploadCvStrategy.parsePdfFile(file)).thenReturn(ParsedCvData.builder()
                .headline("Backend Engineer")
                .summary(java.util.List.of("Experienced engineer"))
                .build());
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvSnapshotResponse response = cvService.createApplicationSnapshot(
                "vanphat15it", "app-123", "Senior Backend Developer", file);

        ArgumentCaptor<CV> savedCv = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(savedCv.capture());

        assertThat(savedCv.getValue().getObjectName()).isEqualTo("uuid-Senior_Backend_Developer.pdf");
        assertThat(savedCv.getValue().getObjectName()).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(savedCv.getValue().getPdfUrl()).isEqualTo("http://minio/cvs-files/uuid-Senior_Backend_Developer.pdf");
        assertThat(savedCv.getValue().getApplicationId()).isEqualTo("app-123");
        assertThat(response.getCvId()).isNull(); // CV entity has no id assigned by the mock save
    }

    @Test
    void createApplicationSnapshot_passesJobNameToFileServiceForNaming() throws Exception {
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[] { 1 });

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-Data_Scientist.pdf");
        when(fileService.generateFileUrl(anyString())).thenReturn("http://minio/cvs-files/uuid-Data_Scientist.pdf");
        when(uploadCvStrategy.parsePdfFile(file)).thenReturn(ParsedCvData.builder().summary(java.util.List.of()).build());
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cvService.createApplicationSnapshot("vanphat15it", "app-456", "Data Scientist", file);

        verify(fileService).uploadCV(file, "Data Scientist");
    }
}
