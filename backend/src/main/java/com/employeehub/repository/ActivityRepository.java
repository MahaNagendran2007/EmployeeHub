package com.employeehub.repository;

import com.employeehub.model.ActivityLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityRepository extends MongoRepository<ActivityLog, String> {
}
