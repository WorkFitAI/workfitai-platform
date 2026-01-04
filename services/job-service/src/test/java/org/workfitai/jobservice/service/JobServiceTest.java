package org.workfitai.jobservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResCreateJobDTO;
import org.workfitai.jobservice.model.mapper.JobMapper;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;
import org.workfitai.jobservice.service.impl.JobService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Spy
    JobMapper mapper = JobMapper.INSTANCE;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private JobRepository jobRepository;
    @InjectMocks
    private JobService jobService;

    @Test
    void createJob_success() {
        // --- Arrange ---
        UUID skillId1 = UUID.randomUUID();
        UUID skillId2 = UUID.randomUUID();

        ReqJobDTO reqJobDTO = new ReqJobDTO();
        reqJobDTO.setTitle("Java Developer");
        reqJobDTO.setCompanyNo("FPT#25");
        reqJobDTO.setSkillIds(List.of(skillId1, skillId2));

        Company company = Company.builder()
                .companyNo("FPT#25")
                .name("FPT Software")
                .build();

        Skill skill1 = Skill.builder()
                .skillId(skillId1)
                .name("Java")
                .build();

        Skill skill2 = Skill.builder()
                .skillId(skillId2)
                .name("Spring Boot")
                .build();

        // Mock cho mapper (mapCompany, mapSkills)
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company));
        when(skillRepository.findAllById(List.of(skillId1, skillId2))).thenReturn(List.of(skill1, skill2));

        // Mock cho checkJobSkills
        when(skillRepository.findByIdIn(List.of(skillId1, skillId2))).thenReturn(List.of(skill1, skill2));

        // Mock cho checkCompany
        when(companyRepository.findById("FPT#25")).thenReturn(Optional.of(company));

        Job savedJob = new Job();
        savedJob.setJobId(UUID.randomUUID());
        savedJob.setTitle("Java Developer");
        savedJob.setCompany(company);
        savedJob.setSkills(List.of(skill1, skill2));

        when(jobRepository.save(Mockito.<Job>any())).thenReturn(savedJob);


        // --- Act ---
        ResCreateJobDTO result = jobService.createJob(reqJobDTO);

        // --- Assert ---
        assertNotNull(result);
        assertEquals(savedJob.getJobId(), result.getPostId());

        // --- Verify flow ---
        // Mapper gọi
        verify(companyRepository, atLeastOnce()).findById("FPT#25");
        verify(skillRepository).findAllById(List.of(skillId1, skillId2));

        // checkJobSkills gọi
        verify(skillRepository).findByIdIn(List.of(skillId1, skillId2));

        // checkCompany gọi
        verify(companyRepository, atLeastOnce()).findById("FPT#25");

        // Save gọi
        verify(jobRepository).save(Mockito.<Job>any(Job.class));
    }
}
