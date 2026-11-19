package com.demo.approval.repository;

import com.demo.approval.domain.entity.CompensationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CompensationTaskRepository extends JpaRepository<CompensationTask, Long> {

    List<CompensationTask> findTop50ByStatusAndNextRunAtLessThanEqualOrderByIdAsc(CompensationTask.Status status, Instant now);

    List<CompensationTask> findByInstanceIdOrderByIdAsc(String instanceId);

    List<CompensationTask> findByStatusOrderByIdAsc(CompensationTask.Status status);

    /**
     * 抢占任务：PENDING → RUNNING，同时 attempts + 1。
     * 立即执行器和定时重试任务可能同时拿到同一个任务，CAS 保证只有一个执行。
     */
    @Modifying
    @Query("""
            update CompensationTask t
               set t.status = :running,
                   t.attempts = t.attempts + 1,
                   t.updatedAt = :now
             where t.id = :id
               and t.status = :pending
            """)
    int claim(@Param("id") Long id,
              @Param("pending") CompensationTask.Status pending,
              @Param("running") CompensationTask.Status running,
              @Param("now") Instant now);

    default int claim(Long id, Instant now) {
        return claim(id, CompensationTask.Status.PENDING, CompensationTask.Status.RUNNING, now);
    }

    /** 执行器崩溃导致 RUNNING 卡死时，超过租约时间放回 PENDING。 */
    @Modifying
    @Query("""
            update CompensationTask t
               set t.status = :pending,
                   t.updatedAt = :now
             where t.status = :running
               and t.updatedAt < :leaseExpiredBefore
            """)
    int releaseExpiredLeases(@Param("leaseExpiredBefore") Instant leaseExpiredBefore,
                             @Param("pending") CompensationTask.Status pending,
                             @Param("running") CompensationTask.Status running,
                             @Param("now") Instant now);

    default int releaseExpiredLeases(Instant leaseExpiredBefore, Instant now) {
        return releaseExpiredLeases(leaseExpiredBefore, CompensationTask.Status.PENDING, CompensationTask.Status.RUNNING, now);
    }
}
