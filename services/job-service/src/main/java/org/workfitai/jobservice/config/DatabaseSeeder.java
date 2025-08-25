//package org.workfitai.jobservice.config;
//
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Service;
//import org.workfitai.jobservice.domain.Company;
//import org.workfitai.jobservice.domain.Skill;
//import org.workfitai.jobservice.repository.CompanyRepository;
//import org.workfitai.jobservice.repository.JobRepository;
//import org.workfitai.jobservice.repository.SkillRepository;
//
//import java.util.List;
//import java.util.UUID;
//
//@Service
//public class DatabaseSeeder implements CommandLineRunner {
//
//    private final CompanyRepository companyRepository;
//    private final JobRepository jobRepository;
//    private final SkillRepository skillRepository;
//
//    public DatabaseSeeder(
//            CompanyRepository companyRepository,
//            JobRepository jobRepository,
//            SkillRepository skillRepository) {
//        this.companyRepository = companyRepository;
//        this.jobRepository = jobRepository;
//        this.skillRepository = skillRepository;
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println(">>> START INIT SAMPLE DATA");
//
//        long countCompanies = this.companyRepository.count();
//        long countJobs = this.jobRepository.count();
//        long countSkills = this.skillRepository.count();
//
//        if (countCompanies == 0) {
//            Company c = new Company();
//            c.setName("CheeseThank JSC");
//            c.setDescription("Một công ty IT chuyên phát triển hệ thống tuyển dụng.");
//            c.setAddress("Hà Nội, Việt Nam");
//            this.companyRepository.save(c);
//        }
//
//        if (countSkills == 0) {
//            List<Skill> arr = List.of(
//                    Skill.builder().skillId(UUID.randomUUID()).name("Java").build(),
//                    Skill.builder().skillId(UUID.randomUUID()).name("Spring Boot").build(),
//                    Skill.builder().skillId(UUID.randomUUID()).name("MySQL").build(),
//                    Skill.builder().skillId(UUID.randomUUID()).name("ReactJS").build()
//            );
//            this.skillRepository.saveAll(arr);
//        }
//
//        if (countCompanies > 0 && countJobs > 0 && countSkills > 0) {
//            System.out.println(">>> SKIP INIT SAMPLE DATA ~ ALREADY HAVE DATA...");
//        } else {
//            System.out.println(">>> END INIT SAMPLE DATA");
//        }
//    }
//}
