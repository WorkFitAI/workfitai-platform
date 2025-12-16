package org.workfitai.jobservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;
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
    private final ReportRepository reportRepository;

    public DatabaseSeeder(
            CompanyRepository companyRepository,
            JobRepository jobRepository,
            SkillRepository skillRepository,
            ReportRepository reportRepository) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.reportRepository = reportRepository;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> START INIT SAMPLE DATA");

        long countCompanies = this.companyRepository.count();
        long countJobs = this.jobRepository.count();
        long countSkills = this.skillRepository.count();

        /* ===================== COMPANY SEED ===================== */
        if (countCompanies == 0) {
            companyRepository.saveAll(List.of(
                    Company.builder()
                            .companyNo("0101248141_FPT") // MST FPT
                            .name("FPT")
                            .description("Một công ty IT chuyên phát triển hệ thống tuyển dụng.")
                            .address("Hà Nội, Việt Nam")
                            .logoUrl("https://res.cloudinary.com/dphibwpag/image/upload/v1765285766/C001_FPT/oz3gqh997t5jbnj2gtmf.png")
                            .build(),

                    Company.builder()
                            .companyNo("0309807306_KMS") // MST KMS Technology VN
                            .name("KMS Technology")
                            .description("Công ty công nghệ chuyên cung cấp dịch vụ phát triển phần mềm.")
                            .address("TP. Hồ Chí Minh, Việt Nam")
                            .logoUrl("https://res.cloudinary.com/dphibwpag/image/upload/v1765287535/C002_KMS/s6p2prygdxoabcxrkqwp.png")
                            .build(),

                    Company.builder()
                            .companyNo("0303881597_VNG") // MST VNG Corporation
                            .name("VNG Corporation")
                            .description("Công ty công nghệ nổi tiếng về game và các dịch vụ online.")
                            .address("Hà Nội, Việt Nam")
                            .logoUrl("https://res.cloudinary.com/dphibwpag/image/upload/v1765287817/C003_VNG/zdk3cw5u04nkmwbqkwjx.png")
                            .build(),

                    Company.builder()
                            .companyNo("0302731081_TMA") // MST TMA Solutions
                            .name("TMA Solutions")
                            .description("Công ty gia công phần mềm chất lượng cao cho thị trường quốc tế.")
                            .address("Đà Nẵng, Việt Nam")
                            .logoUrl("https://res.cloudinary.com/dphibwpag/image/upload/v1765288090/C004_TMA/xqopm60ngq265e7518oa.jpg")
                            .build(),

                    Company.builder()
                            .companyNo("0100109106_VNPT") // MST VNPT
                            .name("VNPT")
                            .description("Tập đoàn viễn thông hàng đầu Việt Nam.")
                            .address("Hà Nội, Việt Nam")
                            .logoUrl("https://res.cloudinary.com/dphibwpag/image/upload/v1765288202/C005_VNPT/zoomsuffrk47p8qjy5xu.jpg")
                            .build(),

                    Company.builder()
                            .companyNo("0100109106_TC")  // MST TechCorp
                            .name("TechCorp Vietnam")
                            .description("Công ty công nghệ hàng đầu Việt Nam.")
                            .address("Hồ Chí Minh, Việt Nam")
                            .logoUrl("https://example.com/techcorp-logo.jpg")
                            .build()
            ));
        }

        /* ===================== SKILL SEED ===================== */
        if (countSkills == 0) {
            skillRepository.saveAll(List.of(
                    new Skill("Java"),
                    new Skill("Spring Boot"),
                    new Skill("MySQL"),
                    new Skill("ReactJS"),
                    new Skill("Docker"),
                    new Skill(".NET"),
                    new Skill("Python"),
                    new Skill("AWS"),
                    new Skill("Angular"),
                    new Skill("Kubernetes")
            ));
        }

        /* ===================== JOB SEED ===================== */
        if (countJobs == 0) {

            List<Company> companies = companyRepository.findAll();
            List<Skill> allSkills = skillRepository.findAll();
            Random rand = new Random();

            List<Job> jobs = new ArrayList<>();

            for (int i = 1; i <= 30; i++) {

                Company company = companies.get(rand.nextInt(companies.size()));

                EmploymentType employmentType = (i % 3 == 0)
                        ? EmploymentType.PART_TIME
                        : EmploymentType.FULL_TIME;

                ExperienceLevel expLevel =
                        (i % 3 == 0) ? ExperienceLevel.JUNIOR :
                                (i % 3 == 1) ? ExperienceLevel.MID :
                                        ExperienceLevel.SENIOR;

                BigDecimal salaryMin = BigDecimal.valueOf(1000 + rand.nextInt(1000));
                BigDecimal salaryMax = salaryMin.add(BigDecimal.valueOf(500 + rand.nextInt(1000)));

                List<Skill> jobSkills = new ArrayList<>();
                for (int s = 0; s < 3; s++) {
                    jobSkills.add(allSkills.get(rand.nextInt(allSkills.size())));
                }

                /* ========= Tạo nội dung text mẫu ========= */
                String longDesc =
                        "Mô tả chi tiết công việc cho vị trí " +
                                ((i % 2 == 0) ? "Backend" : "Frontend") +
                                " Developer #" + i +
                                ".\nỨng viên sẽ tham gia phát triển hệ thống theo mô hình Agile, " +
                                "review code, tối ưu hiệu năng và làm việc trực tiếp với đội ngũ kỹ thuật. " +
                                "Cần khả năng giải quyết vấn đề tốt và tinh thần học hỏi. " +
                                "Cơ hội được tiếp cận cloud, CI/CD và microservices.";

                String shortDesc =
                        "Tuyển " + expLevel.name() + " Developer tham gia phát triển dự án hệ thống nội bộ.";

                String benefits =
                        "- Lương thưởng hấp dẫn\n" +
                                "- Bảo hiểm full lương\n" +
                                "- Cơ hội onsite quốc tế\n" +
                                "- Môi trường chuyên nghiệp";

                String requirements =
                        "- Kinh nghiệm " + rand.nextInt(4) + "–" + (2 + rand.nextInt(3)) + " năm\n" +
                                "- Nắm vững cấu trúc dữ liệu và thuật toán\n" +
                                "- Hiểu biết về hệ thống phân tán\n" +
                                "- Kỹ năng teamwork tốt";

                String responsibilities =
                        "- Phát triển và bảo trì hệ thống\n" +
                                "- Viết unit test\n" +
                                "- Code review cho team\n" +
                                "- Tối ưu hiệu năng module";

                String requiredExperience =
                        expLevel == ExperienceLevel.JUNIOR ? "0-1 năm" :
                                expLevel == ExperienceLevel.MID ? "2-4 năm" :
                                        "5+ năm";

                /* ========= Build Job ========= */
                Job job = Job.builder()
                        .title((expLevel == ExperienceLevel.JUNIOR ? "Junior" :
                                expLevel == ExperienceLevel.MID ? "Mid" : "Senior")
                                + " " + ((i % 2 == 0) ? "Backend" : "Frontend") + " Developer #" + i)
                        .description(longDesc)
                        .shortDescription(shortDesc)
                        .employmentType(employmentType)
                        .experienceLevel(expLevel)
                        .requiredExperience(requiredExperience)
                        .salaryMin(salaryMin)
                        .salaryMax(salaryMax)
                        .views((long) rand.nextInt(1000))
                        .currency("USD")
                        .location(company.getAddress())
                        .quantity(1 + rand.nextInt(5))
                        .totalApplications(0)
                        .expiresAt(Instant.now().plusSeconds(60L * 60 * 24 * (15 + rand.nextInt(30))))
                        .status(JobStatus.PUBLISHED)
                        .educationLevel("Đại học CNTT")
                        .benefits(benefits)
                        .requirements(requirements)
                        .responsibilities(responsibilities)
                        .company(company)
                        .skills(jobSkills)
                        .build();

                jobs.add(job);
            }

            jobRepository.saveAll(jobs);
        }

        /* ===================== REPORT SEED ===================== */
        long countReports = reportRepository.count();
        if (countReports == 0) {
            List<Job> jobs = jobRepository.findAll().subList(0, 4);
            List<Report> reports = new ArrayList<>();
            Random rand = new Random();

            int totalReports = 0;
            while (totalReports < 20) {
                for (Job job : jobs) {
                    int reportsForJob = 1 + rand.nextInt(5);
                    for (int i = 0; i < reportsForJob && totalReports < 20; i++) {
                        Report report = Report.builder()
                                .job(job)
                                .reportContent("Nội dung report #" + (i + 1) + " cho job: " + job.getTitle())
                                .status(EReportStatus.PENDING)
                                .build();
                        reports.add(report);
                        totalReports++;
                    }
                }
            }

            reportRepository.saveAll(reports);
            System.out.println(">>> Seeded " + reports.size() + " reports");
        }
        System.out.println(">>> END INIT SAMPLE DATA");
    }
}
