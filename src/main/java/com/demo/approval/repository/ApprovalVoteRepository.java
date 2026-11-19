package com.demo.approval.repository;

import com.demo.approval.domain.entity.ApprovalVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalVoteRepository extends JpaRepository<ApprovalVote, Long> {

    List<ApprovalVote> findByInstanceIdAndNodeIdAndRoundNo(String instanceId, String nodeId, int roundNo);

    boolean existsByInstanceIdAndNodeIdAndRoundNoAndApproverId(String instanceId, String nodeId, int roundNo, String approverId);

    List<ApprovalVote> findByInstanceIdOrderByIdAsc(String instanceId);
}
