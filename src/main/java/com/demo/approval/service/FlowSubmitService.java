package com.demo.approval.service;

import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.entity.FlowTemplate;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowEventType;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.repository.FlowInstanceRepository;
import com.demo.approval.sync.FlowEventPublisher;
import com.demo.approval.web.dto.FlowInstanceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** 提交审批：拷贝模板快照 → 创建实例(version=1) → 锁预算 → 发起点待办 → 写事件。 */
@Service
public class FlowSubmitService {

    private static final Logger log = LoggerFactory.getLogger(FlowSubmitService.class);

    private final TemplateService templateService;
    private final FlowInstanceRepository instanceRepository;
    private final BudgetService budgetService;
    private final TodoService todoService;
    private final FlowQueryService queryService;
    private final FlowEventPublisher eventPublisher;
    private final JsonCodec jsonCodec;

    public FlowSubmitService(TemplateService templateService,
                             FlowInstanceRepository instanceRepository,
                             BudgetService budgetService,
                             TodoService todoService,
                             FlowQueryService queryService,
                             FlowEventPublisher eventPublisher,
                             JsonCodec jsonCodec) {
        this.templateService = templateService;
        this.instanceRepository = instanceRepository;
        this.budgetService = budgetService;
        this.todoService = todoService;
        this.queryService = queryService;
        this.eventPublisher = eventPublisher;
        this.jsonCodec = jsonCodec;
    }

    @Transactional
    public FlowInstanceView submit(String templateKey, String initiatorId, Map<String, Object> form) {
        FlowTemplate template = templateService.requireLatest(templateKey);
        FlowDefinition definition = jsonCodec.read(template.getDefinitionJson(), FlowDefinition.class);
        FlowNodeDefinition startNode = definition.requireNode(definition.startNodeId());

        // 快照隔离：template.getDefinitionJson() 原样拷进实例，之后模板再发布新版本也影响不到这张单子
        FlowInstance instance = FlowInstance.start(
                UUID.randomUUID().toString(), template, jsonCodec.write(form), initiatorId, startNode.nodeId());
        instanceRepository.saveAndFlush(instance);

        budgetService.lockIfPresent(instance.getId(), form);
        todoService.createForNode(instance.getId(), startNode.nodeId(), instance.getRoundNo(), startNode.approverIds());

        FlowInstanceView view = queryService.getView(instance.getId());
        eventPublisher.publish(instance.getId(), view.version(), FlowEventType.SUBMITTED, initiatorId, view.toPatch());

        log.info("[提交] instance={} template={}@v{} initiator={} startNode={}",
                instance.getId(), templateKey, template.getTemplateVersion(), initiatorId, startNode.nodeId());
        return view;
    }
}
