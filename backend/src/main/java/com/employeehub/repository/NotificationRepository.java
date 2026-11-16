package com.employeehub.repository;

import com.employeehub.model.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    @Query("{ $or: [ { recipientUsername: ?0 }, { recipientRoles: ?1 } ] }")
    List<Notification> findVisible(String username, String role, Pageable pageable);

    @Query(value = "{ $and: [ { $or: [ { recipientUsername: ?0 }, { recipientRoles: ?1 } ] }, { readBy: { $ne: ?0 } } ] }", count = true)
    long countUnread(String username, String role);
}
