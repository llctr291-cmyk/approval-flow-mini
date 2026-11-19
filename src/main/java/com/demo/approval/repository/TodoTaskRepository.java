package com.demo.approval.repository;

import com.demo.approval.domain.entity.TodoTask;
import com.demo.approval.domain.model.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TodoTaskRepository extends JpaRepository<TodoTask, Long> {

    List<TodoTask> findByAssigneeIdAndStatusOrderByCreatedAtDesc(String assigneeId, TodoStatus status);

    List<TodoTask> findByInstanceIdOrderByIdAsc(String instanceId);

    boolean existsByInstanceIdAndNodeIdAndRoundNoAndAssigneeId(String instanceId, String nodeId, int roundNo, String assigneeId);

    /** 单人待办状态流转（完成自己的待办）。带 from 条件，重复执行是幂等的。 */
    @Modifying
    @Query("""
            update TodoTask t set t.status = :to, t.updatedAt = :now
             where t.instanceId = :instanceId and t.nodeId = :nodeId and t.roundNo = :roundNo
               and t.assigneeId = :assigneeId and t.status = :from
            """)
    int transitionOne(@Param("instanceId") String instanceId,
                      @Param("nodeId") String nodeId,
                      @Param("roundNo") int roundNo,
                      @Param("assigneeId") String assigneeId,
                      @Param("from") TodoStatus from,
                      @Param("to") TodoStatus to,
                      @Param("now") Instant now);

    /** 整个节点的待办批量流转（或签通过后撤销其他人的待办）。 */
    @Modifying
    @Query("""
            update TodoTask t set t.status = :to, t.updatedAt = :now
             where t.instanceId = :instanceId and t.nodeId = :nodeId and t.roundNo = :roundNo
               and t.status = :from
            """)
    int transitionNode(@Param("instanceId") String instanceId,
                       @Param("nodeId") String nodeId,
                       @Param("roundNo") int roundNo,
                       @Param("from") TodoStatus from,
                       @Param("to") TodoStatus to,
                       @Param("now") Instant now);

    /** 某一轮次所有待办批量流转（回退补偿、驳回时使用）。 */
    @Modifying
    @Query("""
            update TodoTask t set t.status = :to, t.updatedAt = :now
             where t.instanceId = :instanceId and t.roundNo = :roundNo and t.status = :from
            """)
    int transitionRound(@Param("instanceId") String instanceId,
                        @Param("roundNo") int roundNo,
                        @Param("from") TodoStatus from,
                        @Param("to") TodoStatus to,
                        @Param("now") Instant now);
}
