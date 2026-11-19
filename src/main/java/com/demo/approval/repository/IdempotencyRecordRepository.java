package com.demo.approval.repository;

import com.demo.approval.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
