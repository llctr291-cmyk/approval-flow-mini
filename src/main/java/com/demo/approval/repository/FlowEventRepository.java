package com.demo.approval.repository;

import com.demo.approval.domain.entity.FlowEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FlowEventRepository extends JpaRepository<FlowEvent, Long> {

    /**
     * 按游标补拉：seq > afterSeq，并且只返回 visibleBefore 之前写入的事件（读延迟窗口），
     * instanceId 为空时返回所有实例的事件。
     */
    @Query("""
            select e from FlowEvent e
             where e.seq > :afterSeq
               and e.createdAt <= :visibleBefore
               and (:instanceId is null or e.instanceId = :instanceId)
             order by e.seq asc
            """)
    List<FlowEvent> findPage(@Param("afterSeq") long afterSeq,
                             @Param("instanceId") String instanceId,
                             @Param("visibleBefore") Instant visibleBefore,
                             Pageable pageable);

    List<FlowEvent> findByInstanceIdOrderBySeqAsc(String instanceId);
}
