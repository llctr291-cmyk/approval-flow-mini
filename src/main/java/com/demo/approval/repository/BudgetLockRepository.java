package com.demo.approval.repository;

import com.demo.approval.domain.entity.BudgetLock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLockRepository extends JpaRepository<BudgetLock, String> {
}
