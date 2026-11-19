package com.demo.approval.repository;

import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FlowInstanceRepository extends JpaRepository<FlowInstance, String> {

    /**
     * CAS 写库 —— 并发控制的核心。
     *
     * <pre>
     * UPDATE flow_instance SET ..., version = version + 1
     * WHERE id = ? AND version = ?
     * </pre>
     *
     * 返回影响行数：1 = 抢到了；0 = 版本已经被别人改过，调用方必须抛冲突异常让整个事务回滚
     * （连同本事务里插入的投票一起撤销）。
     *
     * <p>flushAutomatically：先把本事务里待插入的投票刷到库里，唯一约束冲突能尽早暴露；
     * clearAutomatically：清掉一级缓存，之后再查实例拿到的是数据库里的最新值。</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FlowInstance i
               set i.currentNodeId = :nodeId,
                   i.status        = :status,
                   i.roundNo       = :roundNo,
                   i.visitedPath   = :visitedPath,
                   i.version       = i.version + 1,
                   i.updatedAt     = :now
             where i.id = :id
               and i.version = :expectedVersion
            """)
    int compareAndSet(@Param("id") String id,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("nodeId") String nodeId,
                      @Param("status") InstanceStatus status,
                      @Param("roundNo") int roundNo,
                      @Param("visitedPath") String visitedPath,
                      @Param("now") Instant now);

    List<FlowInstance> findByInitiatorIdOrderByCreatedAtDesc(String initiatorId);
}
