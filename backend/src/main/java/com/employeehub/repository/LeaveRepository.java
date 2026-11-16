package com.employeehub.repository;

import com.employeehub.model.LeaveRequest;
import com.employeehub.model.LeaveStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LeaveRepository extends MongoRepository<LeaveRequest, String> {

    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByEmployeeId(String employeeId, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdAndStatus(String employeeId, LeaveStatus status, Pageable pageable);

    /** Existing requests of the same employee whose dates intersect [start, end]. */
    List<LeaveRequest> findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String employeeId, Collection<LeaveStatus> statuses, LocalDate end, LocalDate start);

    /** Requests with the given status that cover the given day. */
    List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveStatus status, LocalDate day, LocalDate sameDay);

    long countByStatus(LeaveStatus status);
}
