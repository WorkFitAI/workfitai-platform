package org.workfitai.jobservice.service;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.document.JobDocument;
import org.workfitai.jobservice.model.dto.response.ElasticSearchResult;
import org.workfitai.jobservice.model.mapper.JobDocumentMapper;
import org.workfitai.jobservice.repository.JobRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticJobService {

        private final ElasticsearchClient client;
        private final JobRepository jobRepository;
        private final JobDocumentMapper mapper;

        private static final String JOB_INDEX = "jobs";

        public ElasticSearchResult search(String keyword, Pageable pageable) throws IOException {

                SearchResponse<JobDocument> response = client.search(s -> s
                                .index(JOB_INDEX)
                                .size(10000)
                                .from((int) pageable.getOffset())
                                .size(pageable.getPageSize())
                                .query(q -> q.bool(b -> b

                                                .should(sh -> sh.match(m -> m
                                                                .field("title")
                                                                .query(keyword)
                                                                .boost(5.0f)
                                                                .fuzziness("2")))

                                                .should(sh -> sh.matchPhrasePrefix(m -> m
                                                                .field("title")
                                                                .query(keyword)
                                                                .boost(4.0f)))

                                                .should(sh -> sh.match(m -> m
                                                                .field("skills")
                                                                .query(keyword)
                                                                .boost(3.0f)
                                                                .fuzziness("1")))

                                                .should(sh -> sh.match(m -> m
                                                                .field("companyName")
                                                                .query(keyword)
                                                                .boost(2.0f)
                                                                .fuzziness("1")))

                                                .should(sh -> sh.match(m -> m
                                                                .field("description")
                                                                .query(keyword)))

                                                .minimumShouldMatch("1"))),
                                JobDocument.class);

                log.info("Elastic hits size: {}", response.hits().hits().size());
                log.info("Elastic total: {}", response.hits().total().value());

                response.hits().hits()
                                .forEach(hit -> log.info("Elastic id: {}", hit.source().getJobId()));

                return ElasticSearchResult.builder()
                                .ids(response.hits()
                                                .hits()
                                                .stream()
                                                .map(hit -> hit.source().getJobId())
                                                .toList())
                                .total(response.hits().total().value())
                                .build();
        }

        @Transactional
        public void saveToElastic(Job job) throws IOException {

                JobDocument document = mapper.toDocument(job);

                client.index(i -> i
                                .index(JOB_INDEX)
                                .id(job.getJobId().toString())
                                .document(document));
        }

        public void deleteIndex() {
                try {

                        boolean exists = client.indices()
                                        .exists(e -> e.index(JOB_INDEX))
                                        .value();

                        if (!exists) {
                                return;
                        }

                        client.indices().delete(d -> d.index(JOB_INDEX));

                } catch (IOException e) {
                        throw new RuntimeException("Failed to delete Elasticsearch index", e);
                }
        }

        public void createIndex() {
                try {

                        boolean exists = client.indices()
                                        .exists(e -> e.index(JOB_INDEX))
                                        .value();

                        if (exists) {
                                log.debug("Index {} already exists", JOB_INDEX);
                                return;
                        }

                        client.indices().create(c -> c.index(JOB_INDEX));

                        log.debug("Created index {}", JOB_INDEX);

                } catch (IOException e) {
                        log.error("Failed to create index {}", JOB_INDEX, e);
                        throw new RuntimeException("Failed to create Elasticsearch index.", e);
                }
        }

        @Transactional(readOnly = true)
        public void rebuildJobIndex() {
                deleteIndex();
                createIndex();

                int page = 0;
                int size = 500;

                Page<Job> jobs;

                do {

                        jobs = jobRepository.findAll(PageRequest.of(page, size));

                        BulkRequest.Builder bulk = new BulkRequest.Builder();

                        for (Job job : jobs.getContent()) {

                                bulk.operations(op -> op.index(idx -> idx
                                                .index(JOB_INDEX)
                                                .id(job.getJobId().toString())
                                                .document(mapper.toDocument(job))));
                        }

                        try {
                                client.bulk(bulk.build());
                        } catch (ElasticsearchException | IOException e) {
                                log.error("Failed to index jobs", e);
                        }

                        log.info("Indexed {} jobs", jobs.getContent().size());

                        page++;

                } while (jobs.hasNext());

                try {
                        client.indices().refresh(r -> r.index(JOB_INDEX));
                } catch (ElasticsearchException | IOException e) {
                        log.error("Failed to refresh index", e);
                }

                log.info("Rebuild completed");
        }
}
