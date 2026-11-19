package com.demo.approval.repository;

import com.demo.approval.domain.entity.ExtraApprover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraApproverRepository extends JpaRepository<ExtraApprover, Long> {

    List<ExtraApprover> findByInstanceIdAndNodeIdAndRoundNo(String instanceId, String nodeId, int roundNo);

    boolean existsByInstanceIdAndNodeIdAndRoundNoAndApproverId(String instanceId, String nodeId, int roundNo, String approverId);
}
