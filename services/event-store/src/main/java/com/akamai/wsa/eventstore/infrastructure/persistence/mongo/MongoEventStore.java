package com.akamai.wsa.eventstore.infrastructure.persistence.mongo;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "wsa.storage", havingValue = "mongo")
public class MongoEventStore implements EventStore {

    private final MongoTemplate mongoTemplate;

    public MongoEventStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void ensureIndexes() {
        IndexOperations indexOperations = mongoTemplate.indexOps(StoredEventDocument.class);
        indexOperations.ensureIndex(new Index()
                .on("configId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .named("configId_timestamp"));
        indexOperations.ensureIndex(new Index()
                .on("clientIp", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .named("clientIp_timestamp"));
        indexOperations.ensureIndex(new Index()
                .on("timestamp", Sort.Direction.DESC)
                .named("timestamp"));
    }

    @Override
    public void saveAll(List<StoredEvent> storedEvents) {
        for (StoredEvent storedEvent : storedEvents) {
            mongoTemplate.save(StoredEventDocumentMapper.toDocument(storedEvent));
        }
    }

    @Override
    public long countAll() {
        return mongoTemplate.count(new Query(), StoredEventDocument.class);
    }

    @Override
    public List<StoredEvent> findByConfigId(int configId) {
        return mongoTemplate.find(Query.query(Criteria.where("configId").is(configId)), StoredEventDocument.class)
                .stream()
                .map(StoredEventDocumentMapper::toStoredEvent)
                .toList();
    }
}
