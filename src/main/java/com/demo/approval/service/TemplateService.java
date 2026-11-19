package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.FlowTemplate;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.domain.model.FlowTransition;
import com.demo.approval.engine.FlowRoutingEngine;
import com.demo.approval.repository.FlowTemplateRepository;
import com.demo.approval.web.dto.TemplateView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模板发布。每次发布都是新版本（不可变），配合实例快照实现版本隔离：
 * 改模板只影响之后新提交的单子。
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final FlowTemplateRepository repository;
    private final FlowRoutingEngine routingEngine;
    private final JsonCodec jsonCodec;

    public TemplateService(FlowTemplateRepository repository, FlowRoutingEngine routingEngine, JsonCodec jsonCodec) {
        this.repository = repository;
        this.routingEngine = routingEngine;
        this.jsonCodec = jsonCodec;
    }

    @Transactional
    public TemplateView publish(String templateKey, FlowDefinition definition, String publisherId) {
        definition.validate();
        for (FlowNodeDefinition node : definition.nodes()) {
            node.transitions().stream()
                    .filter(transition -> !transition.isDefault())
                    .map(FlowTransition::condition)
                    .forEach(routingEngine::precompile);
        }
        int nextVersion = repository.findTopByTemplateKeyOrderByTemplateVersionDesc(templateKey)
                .map(latest -> latest.getTemplateVersion() + 1)
                .orElse(1);
        // 并发发布同一个模板时，唯一约束 (key, version) 会让后到的失败，避免版本号重复
        FlowTemplate saved = repository.saveAndFlush(
                new FlowTemplate(templateKey, nextVersion, jsonCodec.write(definition), publisherId));
        log.info("[模板] 发布 key={} version={} by={}", templateKey, nextVersion, publisherId);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public FlowTemplate requireLatest(String templateKey) {
        return repository.findTopByTemplateKeyOrderByTemplateVersionDesc(templateKey)
                .orElseThrow(() -> BusinessException.notFound("流程模板", templateKey));
    }

    @Transactional(readOnly = true)
    public TemplateView latest(String templateKey) {
        return toView(requireLatest(templateKey));
    }

    @Transactional(readOnly = true)
    public List<TemplateView> history(String templateKey) {
        return repository.findByTemplateKeyOrderByTemplateVersionDesc(templateKey).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean exists(String templateKey) {
        return repository.findTopByTemplateKeyOrderByTemplateVersionDesc(templateKey).isPresent();
    }

    private TemplateView toView(FlowTemplate template) {
        return new TemplateView(
                template.getTemplateKey(),
                template.getTemplateVersion(),
                jsonCodec.read(template.getDefinitionJson(), FlowDefinition.class),
                template.getPublishedBy(),
                template.getCreatedAt());
    }
}
