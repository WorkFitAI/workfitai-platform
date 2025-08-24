package org.workfitai.jobservice.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {
//
//    @Mock
//    private JobRepository jobRepository;
//
//    @Mock
//    private CompanyRepository companyRepository;
//
//    @Mock
//    private SkillRepository skillRepository;
//
//    @Mock
//    private JobMapper jobMapper;
//
//    @InjectMocks
//    private JobService jobService;
//
//    private Job dbJob;
//    private Job updateRequest;
//
//    private UUID jobId;
//
//    @BeforeEach
//    void setUp() {
//        jobId = UUID.randomUUID();
//
//        dbJob = Job.builder()
//                .jobId(jobId)
//                .title("Old Title")
//                .description("Old Description")
//                .location("HCM")
//                .salaryMin(BigDecimal.valueOf(1000))
//                .salaryMax(BigDecimal.valueOf(2000))
//                .experienceLevel(ExperienceLevel.JUNIOR)
//                .employmentType(EmploymentType.FULL_TIME)
//                .skills(Collections.emptyList())
//                .build();
//
//        updateRequest = Job.builder()
//                .title("New Title")
//                .description("New Description")
//                .location("HN")
//                .salaryMin(BigDecimal.valueOf(1500))
//                .salaryMax(BigDecimal.valueOf(3000))
//                .experienceLevel(ExperienceLevel.SENIOR)
//                .employmentType(EmploymentType.PART_TIME)
//                .skills(Collections.emptyList())
//                .build();
//    }
//
//    @Test
//    void createJob_shouldReturnJob_WhenValidInput() {
//        Company inputCompany = new Company();
//        inputCompany.setCompanyNo("OPENAI25");
//        inputCompany.setName("OpenAI");
//
//        Skill java = Skill.builder().skillId(UUID.randomUUID()).name("Java").build();
//        Skill spring = Skill.builder().skillId(UUID.randomUUID()).name("Spring Boot").build();
//
//        Job inputJob = new Job();
//        inputJob.setTitle("Java Backend Developer");
//        inputJob.setExperienceLevel(ExperienceLevel.JUNIOR);
//        inputJob.setCompany(inputCompany);
//        inputJob.setSkills(List.of(java, spring));
//
//        Job outputJob = new Job();
//        outputJob.setJobId(UUID.randomUUID());
//        outputJob.setTitle("Java Backend Developer");
//        outputJob.setExperienceLevel(ExperienceLevel.JUNIOR);
//        outputJob.setCompany(inputCompany);
//        outputJob.setSkills(List.of(java, spring));
//
//        when(companyRepository.findById(inputCompany.getId())).thenReturn(Optional.of(inputCompany));
//        when(skillRepository.findByIdIn(anyList())).thenReturn(List.of(java, spring));
//        when(jobRepository.save(any(Job.class))).thenReturn(outputJob);
//
//        when(jobMapper.toResCreateJobDTO(any(Job.class))).thenAnswer(invocation -> {
//            Job job = invocation.getArgument(0);
//            return ResCreateJobDTO.builder()
//                    .postId(job.getJobId())
//                    .title(job.getTitle())
//                    .experienceLevel(job.getExperienceLevel())
//                    .skillNames(job.getSkills().stream().map(Skill::getName).toList())
//                    .build();
//        });
//
//        ResCreateJobDTO result = jobService.createJob(inputJob);
//
//        assertThat(result).isNotNull();
//        assertThat(result.getTitle()).isEqualTo("Java Backend Developer");
//        assertThat(result.getExperienceLevel()).isEqualTo(ExperienceLevel.JUNIOR);
//        assertThat(result.getSkillNames()).containsExactlyInAnyOrder("Java", "Spring Boot");
//    }
//
//    @Test
//    void testUpdateJob() {
//        ResUpdateJobDTO responseDto = new ResUpdateJobDTO();
//        responseDto.setTitle(updateRequest.getTitle());
//        when(jobMapper.toResUpdateJobDTO(any(Job.class))).thenReturn(responseDto);
//
//        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
//
//        ResUpdateJobDTO result = jobService.updateJob(updateRequest, dbJob);
//
//        verify(jobRepository, times(1)).save(dbJob);
//        verify(jobMapper, times(1)).toResUpdateJobDTO(dbJob);
//
//        assertThat(dbJob.getTitle()).isEqualTo("New Title");
//        assertThat(dbJob.getDescription()).isEqualTo("New Description");
//        assertThat(dbJob.getLocation()).isEqualTo("HN");
//        assertThat(dbJob.getSalaryMin()).isEqualTo(BigDecimal.valueOf(1500));
//        assertThat(dbJob.getSalaryMax()).isEqualTo(BigDecimal.valueOf(3000));
//        assertThat(dbJob.getExperienceLevel()).isEqualTo(ExperienceLevel.SENIOR);
//        assertThat(dbJob.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
//
//        assertThat(result.getTitle()).isEqualTo("New Title");
//    }
//
//    @Test
//    void testFetchJobById_Found() {
//        ResJobDTO resJobDTO = ResJobDTO.builder()
//                .postId(dbJob.getJobId())
//                .title(dbJob.getTitle())
//                .build();
//
//        when(jobRepository.findById(jobId)).thenReturn(Optional.of(dbJob));
//        when(jobMapper.toResJobDTO(dbJob)).thenReturn(resJobDTO);
//
//        ResJobDTO result = jobService.fetchJobById(jobId);
//
//        assertThat(result).isNotNull();
//        assertThat(result.getPostId()).isEqualTo(jobId);
//        assertThat(result.getTitle()).isEqualTo("Old Title");
//
//        verify(jobRepository, times(1)).findById(jobId);
//        verify(jobMapper, times(1)).toResJobDTO(dbJob);
//    }
//
//    @Test
//    void testFetchJobById_NotFound() {
//        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
//
//        ResJobDTO result = jobService.fetchJobById(jobId);
//
//        assertThat(result).isNull();
//
//        verify(jobRepository, times(1)).findById(jobId);
//        verify(jobMapper, never()).toResJobDTO(any(Job.class));
//    }
}
