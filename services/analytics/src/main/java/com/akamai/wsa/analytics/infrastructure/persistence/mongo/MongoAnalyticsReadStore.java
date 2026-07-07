package com.akamai.wsa.analytics.infrastructure.persistence.mongo;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.AttackerStatistics;
import com.akamai.wsa.analytics.domain.query.CategoryStatistics;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.PathStatistics;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MongoAnalyticsReadStore implements AnalyticsReadStore {

    private final MongoTemplate mongoTemplate;

    public MongoAnalyticsReadStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public StatisticsSummary summarize(StatisticsQuery statisticsQuery) {
        Criteria criteria = buildCriteria(statisticsQuery.configId(), statisticsQuery.timeRange(), null, null);
        AggregationOperation match = Aggregation.match(criteria);

        long totalEvents = mongoTemplate.count(new Query(criteria), EnrichedEventDocument.class);

        Map<AttackCategory, CategoryStatistics> byCategory = new EnumMap<>(AttackCategory.class);
        for (Document row : aggregate(
                match,
                Aggregation.group("rule.category").count().as("count").avg("threatScore").as("averageThreatScore"))) {
            String categoryName = row.getString("_id");
            if (categoryName != null) {
                byCategory.put(AttackCategory.valueOf(categoryName),
                        new CategoryStatistics(asLong(row.get("count")), asDouble(row.get("averageThreatScore"))));
            }
        }

        Map<Action, Long> byAction = new EnumMap<>(Action.class);
        for (Document row : aggregate(
                match,
                Aggregation.group("action").count().as("count"))) {
            String actionName = row.getString("_id");
            if (actionName != null) {
                byAction.put(Action.valueOf(actionName), asLong(row.get("count")));
            }
        }

        List<AttackerStatistics> topAttackers = new ArrayList<>();
        for (Document row : aggregate(
                match,
                Aggregation.group("clientIp").count().as("count").avg("threatScore").as("averageThreatScore"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count").and(Sort.by(Sort.Direction.ASC, "_id"))),
                Aggregation.limit(StatisticsSummary.TOP_LIMIT))) {
            topAttackers.add(new AttackerStatistics(row.getString("_id"),
                    asLong(row.get("count")), asDouble(row.get("averageThreatScore"))));
        }

        List<PathStatistics> topTargetedPaths = new ArrayList<>();
        for (Document row : aggregate(
                match,
                Aggregation.group("path").count().as("count"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count").and(Sort.by(Sort.Direction.ASC, "_id"))),
                Aggregation.limit(StatisticsSummary.TOP_LIMIT))) {
            topTargetedPaths.add(new PathStatistics(row.getString("_id"), asLong(row.get("count"))));
        }

        return new StatisticsSummary(statisticsQuery.configId(), statisticsQuery.timeRange(),
                totalEvents, byCategory, byAction, topAttackers, topTargetedPaths);
    }

    @Override
    public EventSamplesPage findSamples(SampleQuery sampleQuery) {
        Criteria criteria = buildCriteria(
                sampleQuery.configId(), sampleQuery.timeRange(), sampleQuery.category(), sampleQuery.action());

        long total = mongoTemplate.count(new Query(criteria), EnrichedEventDocument.class);

        Query pageQuery = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .skip(sampleQuery.offset())
                .limit(sampleQuery.limit());

        List<EnrichedEventView> events = mongoTemplate.find(pageQuery, EnrichedEventDocument.class).stream()
                .map(EnrichedEventDocument::toView)
                .toList();

        return new EventSamplesPage(total, sampleQuery.limit(), sampleQuery.offset(), events);
    }

    private List<Document> aggregate(AggregationOperation... operations) {
        Aggregation aggregation = Aggregation.newAggregation(EnrichedEventDocument.class, operations);
        return mongoTemplate.aggregate(aggregation, EnrichedEventDocument.class, Document.class).getMappedResults();
    }

    private Criteria buildCriteria(Integer configId, TimeRange timeRange, AttackCategory category, Action action) {
        List<Criteria> parts = new ArrayList<>();
        if (configId != null) {
            parts.add(Criteria.where("configId").is(configId));
        }
        if (timeRange != null) {
            Instant from = timeRange.from();
            Instant to = timeRange.to();
            if (from != null && to != null) {
                parts.add(Criteria.where("timestamp").gte(from).lte(to));
            } else if (from != null) {
                parts.add(Criteria.where("timestamp").gte(from));
            } else if (to != null) {
                parts.add(Criteria.where("timestamp").lte(to));
            }
        }
        if (category != null) {
            parts.add(Criteria.where("rule.category").is(category.name()));
        }
        if (action != null) {
            parts.add(Criteria.where("action").is(action.name()));
        }
        if (parts.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    private static long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double asDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}
