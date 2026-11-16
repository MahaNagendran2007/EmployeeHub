package com.employeehub.repository;

import com.employeehub.model.Employee;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class EmployeeSearchRepositoryImpl implements EmployeeSearchRepository {

    private final MongoTemplate mongoTemplate;

    public EmployeeSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Employee> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        Criteria mongoCriteria = toCriteria(criteria);

        long total = mongoTemplate.count(new Query(mongoCriteria), Employee.class);
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Case-insensitive collation so "alice" sorts next to "Alice".
        Query pageQuery = new Query(mongoCriteria)
                .with(pageable)
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
        List<Employee> content = mongoTemplate.find(pageQuery, Employee.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Map<String, Long> countGroupedBy(String field) {
        Aggregation aggregation = Aggregation.newAggregation(Aggregation.group(field).count().as("count"));
        List<Document> rows = mongoTemplate.aggregate(aggregation, Employee.class, Document.class)
                .getMappedResults();

        Map<String, Long> result = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> row.get("_id") != null)
                .sorted((a, b) -> {
                    int byCount = Long.compare(((Number) b.get("count")).longValue(),
                            ((Number) a.get("count")).longValue());
                    return byCount != 0 ? byCount : a.get("_id").toString().compareToIgnoreCase(b.get("_id").toString());
                })
                .forEach(row -> result.put(row.get("_id").toString(), ((Number) row.get("count")).longValue()));
        return result;
    }

    @Override
    public List<Employee> findAllMatching(EmployeeSearchCriteria criteria, Sort sort, int limit) {
        Query query = new Query(toCriteria(criteria)).with(sort).limit(limit)
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
        return mongoTemplate.find(query, Employee.class);
    }

    @Override
    public List<String> distinctValues(String field) {
        return mongoTemplate.findDistinct(new Query(), field, Employee.class, String.class).stream()
                .filter(v -> v != null && !v.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Override
    public Map<String, Long> countJoinedByMonth(LocalDate from, LocalDate toExclusive) {
        Date start = Date.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());
        Date end = Date.from(toExclusive.atStartOfDay(ZoneOffset.UTC).toInstant());
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("joiningDate").gte(start).lt(end)),
                Aggregation.project().and("joiningDate").extractYear().as("year")
                        .and("joiningDate").extractMonth().as("month"),
                Aggregation.group("year", "month").count().as("count"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, Employee.class, Document.class).getMappedResults()) {
            Document id = (Document) row.get("_id");
            String key = String.format("%04d-%02d", ((Number) id.get("year")).intValue(), ((Number) id.get("month")).intValue());
            result.put(key, ((Number) row.get("count")).longValue());
        }
        return result;
    }

    private Criteria toCriteria(EmployeeSearchCriteria c) {
        List<Criteria> parts = new ArrayList<>();

        if (c.search() != null) {
            // Pattern.quote makes user input a literal string, so it cannot inject a regex.
            Pattern pattern = Pattern.compile(Pattern.quote(c.search()), Pattern.CASE_INSENSITIVE);
            parts.add(new Criteria().orOperator(
                    Criteria.where("fullName").regex(pattern),
                    Criteria.where("employeeCode").regex(pattern),
                    Criteria.where("department").regex(pattern),
                    Criteria.where("email").regex(pattern)));
        }
        if (c.department() != null) {
            parts.add(Criteria.where("department").is(c.department()));
        }
        if (c.location() != null) {
            parts.add(Criteria.where("location").is(c.location()));
        }
        if (c.designation() != null) {
            parts.add(Criteria.where("jobTitle").is(c.designation()));
        }
        if (c.status() != null) {
            parts.add(Criteria.where("employmentStatus").is(c.status()));
        }

        return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts);
    }
}
