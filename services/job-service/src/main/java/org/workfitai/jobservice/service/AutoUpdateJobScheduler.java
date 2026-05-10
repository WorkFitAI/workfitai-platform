package org.workfitai.jobservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.service.impl.JobService;
import org.workfitai.jobservice.service.impl.OutboxService;

@Component
public class AutoUpdateJobScheduler {

  private final JobService jobService;
  private final OutboxService outboxService;

  public AutoUpdateJobScheduler(JobService jobService, OutboxService outboxService) {
    this.jobService = jobService;
    this.outboxService = outboxService;
  }

  @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Ho_Chi_Minh")
  public void closeExpiredJobs() {
    List<Job> jobs = jobService.findExpiredJobsToClose();
    if (jobs.isEmpty())
      return;

    Map<String, String> emailMap = jobService.fetchEmailMap(jobs);

    List<OutboxExpiredJobEvent> events = new ArrayList<>();

    for (Job job : jobs) {

      job.setStatus(JobStatus.CLOSED);

      String hrEmail = emailMap.get(job.getCreatedBy());

      JobExpiredEventDTO dto = jobService.buildDTO(job, hrEmail);

      events.add(outboxService.buildEvent("JOB_EXPIRED", dto, job.getId().toString()));
      events.add(outboxService.buildEvent("SEND_MAIL", dto, job.getId().toString()));
    }

    jobService.updateJobsAndEvents(jobs, events);
  }
}
