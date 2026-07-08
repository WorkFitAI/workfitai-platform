package org.workfitai.jobservice.elastic;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.service.ElasticJobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticSyncRunner implements CommandLineRunner {

  private final JobRepository jobRepository;
  private final ElasticJobService elasticJobService;

  @Override
  @Transactional
  public void run(String... args) throws Exception {

    elasticJobService.deleteIndex();

    elasticJobService.createIndex();

    List<Job> jobs = jobRepository.findAll();

    for (Job job : jobs) {
      elasticJobService.saveToElastic(job);
    }

    log.info("Synced {} jobs to Elasticsearch", jobs.size());
  }
}