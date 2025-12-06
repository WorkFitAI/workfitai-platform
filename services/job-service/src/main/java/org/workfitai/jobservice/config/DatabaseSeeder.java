package org.workfitai.jobservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

            Company vng = Company.builder()
                    .companyNo("C003_VNG")
                    .name("VNG Corporation")
                    .description("Công ty công nghệ nổi tiếng về game và các dịch vụ online.")
                    .address("Hà Nội, Việt Nam")
                    .build();

            Company tma = Company.builder()
                    .companyNo("C004_TMA")
                    .name("TMA Solutions")
                    .description("Công ty cung cấp dịch vụ gia công phần mềm cho thị trường quốc tế.")
                    .address("Đà Nẵng, Việt Nam")
                    .build();

            Company vnpt = Company.builder()
                    .companyNo("C005_VNPT")
                    .name("VNPT")
                    .description("Tập đoàn viễn thông hàng đầu Việt Nam.")
                    .address("Hà Nội, Việt Nam")
                    .build();

            companyRepository.saveAll(List.of(fpt, kms, vng, tma, vnpt));
        }

        if (countSkills == 0) {
            List<Skill> skills = List.of(
                    new Skill("Java"),
                    new Skill("Spring Boot"),
                    new Skill("MySQL"),
                    new Skill("ReactJS"),
                    new Skill("Docker"),
                    new Skill(".NET"),
                    new Skill("Python"),
                    new Skill("AWS"),
                    new Skill("Angular"),
                    new Skill("Kubernetes"));
            skillRepository.saveAll(skills);
        }

        if (countJobs == 0) {
            List<Company> companies = companyRepository.findAll();
            List<Skill> allSkills = skillRepository.findAll();
            if (allSkills.isEmpty())
                throw new IllegalStateException("Skills not found in database");

            Random rand = new Random();
            List<Job> jobs = new ArrayList<>();

            for (int i = 1; i <= 24; i++) {
                // Chọn công ty ngẫu nhiên
                Company company = companies.get(rand.nextInt(companies.size()));

                EmploymentType employmentType = (i % 3 == 0) ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME;
                ExperienceLevel expLevel;
                if (i % 3 == 0)
                    expLevel = ExperienceLevel.JUNIOR;
                else if (i % 3 == 1)
                    expLevel = ExperienceLevel.MID;
                else
                    expLevel = ExperienceLevel.SENIOR;

                BigDecimal salaryMin = BigDecimal.valueOf(1000 + rand.nextInt(1000));
                BigDecimal salaryMax = salaryMin.add(BigDecimal.valueOf(500 + rand.nextInt(1000)));

                List<Skill> jobSkills = new ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    jobSkills.add(allSkills.get(rand.nextInt(allSkills.size())));
                }

                Job job = Job.builder()
                        .title((expLevel == ExperienceLevel.JUNIOR ? "Junior"
                                : expLevel == ExperienceLevel.MID ? "Mid" : "Senior") +
                                " " + (i % 2 == 0 ? "Backend" : "Frontend") + " Developer #" + i)
                        .description("Mô tả công việc cho job #" + i)
                        .employmentType(employmentType)
                        .experienceLevel(expLevel)
                        .salaryMin(salaryMin)
                        .salaryMax(salaryMax)
                        .currency("USD")
                        .location(company.getAddress())
                        .quantity(1 + rand.nextInt(5))
                        .totalApplications(0)
                        .expiresAt(Instant.now().plusSeconds(60L * 60 * 24 * (15 + rand.nextInt(30)))) // 15-45 ngày
                        .status(JobStatus.PUBLISHED)
                        .educationLevel("Đại học CNTT")
                        .company(company)
                        .skills(jobSkills)
                        .build();

                jobs.add(job);
            }

            jobRepository.saveAll(jobs);
        }

        if (countCompanies > 0 && countJobs > 0 && countSkills > 0) {
            System.out.println(">>> SKIP INIT SAMPLE DATA ~ ALREADY HAVE DATA...");
        } else {
            System.out.println(">>> END INIT SAMPLE DATA");
        }
    }
}
