package org.workfitai.jobservice.service;

import java.io.IOException;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.document.JobDocument;
import org.workfitai.jobservice.model.dto.response.ElasticSearchResult;
import org.workfitai.jobservice.model.mapper.JobDocumentMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticJobService {

        private final ElasticsearchClient client;
        private final JobDocumentMapper mapper;

        public ElasticSearchResult search(String keyword, Pageable pageable) throws IOException {

                SearchResponse<JobDocument> response = client.search(s -> s
                                .index("jobs")
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
                                .index("jobs")
                                .id(job.getJobId().toString())
                                .document(document));
        }

        public void deleteIndex() throws IOException {

                boolean exists = client.indices()
                                .exists(e -> e.index("jobs"))
                                .value();

                if (exists) {
                        client.indices().delete(d -> d.index("jobs"));
                        log.info("Deleted index jobs");
                } else {
                        log.info("Index jobs does not exist");
                }
        }

        public void createIndex() throws IOException {

                boolean exists = client.indices()
                                .exists(e -> e.index("jobs"))
                                .value();

                if (!exists) {
                        client.indices().create(c -> c
                                        .index("jobs"));
                        log.info("Created index jobs");
                } else {
                        log.info("Index jobs already exists");
                }
        }
}
