package com.demo.approval.repository;

import com.demo.approval.domain.entity.FlowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowTemplateRepository extends JpaRepository<FlowTemplate, Long> {

    Optional<FlowTemplate> findTopByTemplateKeyOrderByTemplateVersionDesc(String templateKey);

    Optional<FlowTemplate> findByTemplateKeyAndTemplateVersion(String templateKey, int templateVersion);

    List<FlowTemplate> findByTemplateKeyOrderByTemplateVersionDesc(String templateKey);
}
