package org.workfitai.jobservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.domain.Company;
import org.workfitai.jobservice.domain.Job;
import org.workfitai.jobservice.domain.Skill;
import org.workfitai.jobservice.domain.enums.EmploymentType;
import org.workfitai.jobservice.domain.enums.ExperienceLevel;
import org.workfitai.jobservice.domain.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DatabaseSeeder implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;

    public DatabaseSeeder(
            CompanyRepository companyRepository,
            JobRepository jobRepository,
            SkillRepository skillRepository) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> START INIT SAMPLE DATA");

        long countCompanies = this.companyRepository.count();
        long countJobs = this.jobRepository.count();
        long countSkills = this.skillRepository.count();

        if (countCompanies == 0) {
            Company fpt = Company.builder()
                    .companyNo("C001_FPT")
                    .name("FPT")
                    .description("Một công ty IT chuyên phát triển hệ thống tuyển dụng.")
                    .address("Hà Nội, Việt Nam")
                    .build();

            Company kms = Company.builder()
                    .companyNo("C002_KMS")
                    .name("KMS Technology")
                    .description("Công ty công nghệ chuyên cung cấp dịch vụ phát triển phần mềm và giải pháp CNTT.")
                    .address("TP. Hồ Chí Minh, Việt Nam")
                    .build();

            companyRepository.save(fpt);
            companyRepository.save(kms);
        }

        if (countSkills == 0) {
            List<Skill> arr = List.of(
                    Skill.builder().skillId(UUID.randomUUID()).name("Java").build(),
                    Skill.builder().skillId(UUID.randomUUID()).name("Spring Boot").build(),
                    Skill.builder().skillId(UUID.randomUUID()).name("MySQL").build(),
                    Skill.builder().skillId(UUID.randomUUID()).name("ReactJS").build(),
                    Skill.builder().skillId(UUID.randomUUID()).name("Docker").build(),
                    Skill.builder().skillId(UUID.randomUUID()).name(".NET").build()
            );
            this.skillRepository.saveAll(arr);
        }

        if (countJobs == 0) {
            // Lấy company FPT và KMS từ DB
            Company fpt = companyRepository.findById("C001_FPT").orElseThrow(IllegalStateException::new);
            Company kms = companyRepository.findById("C002_KMS").orElseThrow(IllegalStateException::new);

            // Lấy vài skill từ DB
            List<Skill> allSkills = skillRepository.findAll();

            if (allSkills.isEmpty()) {
                new Throwable().printStackTrace();
            }

            Job job1 = Job.builder()
                    .title("Java Backend Developer")
                    .description("Tham gia phát triển các hệ thống tuyển dụng quy mô lớn, sử dụng Spring Boot và Microservices.")
                    .employmentType(EmploymentType.FULL_TIME)
                    .experienceLevel(ExperienceLevel.MID)
                    .salaryMin(new BigDecimal("1500"))
                    .salaryMax(new BigDecimal("2500"))
                    .currency("USD")
                    .location("Hà Nội, Việt Nam")
                    .quantity(3)
                    .expiresAt(Instant.now().plusSeconds(60L * 60 * 24 * 30)) // +30 ngày
                    .status(JobStatus.PUBLISHED)
                    .educationLevel("Đại học CNTT")
                    .company(fpt)
                    .skills(allSkills.subList(0, 3)) // ví dụ gán 3 skill đầu
                    .build();

            Job job2 = Job.builder()
                    .title("Frontend ReactJS Developer")
                    .description("Phát triển giao diện web tương tác cao cho ứng dụng tuyển dụng, sử dụng ReactJS và RESTful API.")
                    .employmentType(EmploymentType.FULL_TIME)
                    .experienceLevel(ExperienceLevel.JUNIOR)
                    .salaryMin(new BigDecimal("1000"))
                    .salaryMax(new BigDecimal("1800"))
                    .currency("USD")
                    .location("TP. Hồ Chí Minh, Việt Nam")
                    .quantity(2)
                    .expiresAt(Instant.now().plusSeconds(60L * 60 * 24 * 45)) // +45 ngày
                    .status(JobStatus.DRAFT)
                    .educationLevel("Cao đẳng/Đại học CNTT")
                    .company(kms)
                    .skills(allSkills.subList(2, 5)) // ví dụ gán 3 skill khác
                    .build();

            jobRepository.saveAll(List.of(job1, job2));
        }


        if (countCompanies > 0 && countJobs > 0 && countSkills > 0) {
            System.out.println(">>> SKIP INIT SAMPLE DATA ~ ALREADY HAVE DATA...");
        } else {
            System.out.println(">>> END INIT SAMPLE DATA");
        }
    }
}
