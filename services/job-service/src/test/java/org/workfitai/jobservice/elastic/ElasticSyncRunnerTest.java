package org.workfitai.jobservice.elastic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.service.ElasticJobService;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticSyncRunnerTest {

  @Mock
  private JobRepository jobRepository;

  @Mock
  private ElasticJobService elasticJobService;

  @InjectMocks
  private ElasticSyncRunner runner;

  @Test
  void run_shouldDeleteCreateAndSyncAllJobs() throws Exception {

    Job job1 = new Job();
    Job job2 = new Job();

    when(jobRepository.findAll()).thenReturn(List.of(job1, job2));

    runner.run();

    verify(elasticJobService).deleteIndex();
    verify(elasticJobService).createIndex();

    verify(jobRepository).findAll();

    verify(elasticJobService).saveToElastic(job1);
    verify(elasticJobService).saveToElastic(job2);

    verifyNoMoreInteractions(elasticJobService);
  }

  @Test
  void run_shouldHandleEmptyJobList() throws Exception {

    when(jobRepository.findAll()).thenReturn(List.of());

    runner.run();

    verify(elasticJobService).deleteIndex();
    verify(elasticJobService).createIndex();

    verify(jobRepository).findAll();

    verify(elasticJobService, never()).saveToElastic(any());

    verifyNoMoreInteractions(elasticJobService);
  }
}
