package com.employeehub.service;

import com.employeehub.dto.ActivityResponse;
import com.employeehub.dto.PageResponse;
import com.employeehub.model.ActivityLog;
import com.employeehub.repository.ActivityRepository;
import com.employeehub.security.CurrentUser;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Persists an audit trail. Recording never breaks the business operation that triggered it. */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository repository;
    private final Clock clock;
    private final int maxPageSize;

    public ActivityService(ActivityRepository repository, Clock clock,
                           @Value("${app.pagination.max-page-size:100}") int maxPageSize) {
        this.repository = repository;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    public void record(String action, String entityType, String entityId, String summary) {
        record(CurrentUser.username(), action, entityType, entityId, summary);
    }

    /** Use this overload when the actor is not the authenticated principal (e.g. a failed login). */
    public void record(String actor, String action, String entityType, String entityId, String summary) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setTimestamp(clock.instant());
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setSummary(summary == null ? "" : summary.substring(0, Math.min(summary.length(), 200)));
            entry.setActor(actor);
            repository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Could not store activity entry {}: {}", action, ex.getClass().getSimpleName());
        }
    }

    public PageResponse<ActivityResponse> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), maxPageSize);
        return PageResponse.from(repository
                .findAll(PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "timestamp")))
                .map(a -> new ActivityResponse(a.getId(), a.getTimestamp(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getSummary(), a.getActor())));
    }
}
