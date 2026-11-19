package com.demo.approval.web;

import com.demo.approval.service.AddSignerService;
import com.demo.approval.service.ApprovalService;
import com.demo.approval.service.FlowQueryService;
import com.demo.approval.service.FlowSubmitService;
import com.demo.approval.service.RollbackService;
import com.demo.approval.service.command.AddSignerCommand;
import com.demo.approval.service.command.ApproveCommand;
import com.demo.approval.service.command.RollbackCommand;
import com.demo.approval.sync.FlowEventQueryService;
import com.demo.approval.web.dto.AddSignerRequest;
import com.demo.approval.web.dto.ApproveRequest;
import com.demo.approval.web.dto.EventPage;
import com.demo.approval.web.dto.FlowInstanceView;
import com.demo.approval.web.dto.RollbackRequest;
import com.demo.approval.web.dto.SubmitRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批接口。原型阶段用 X-User-Id 头模拟登录用户。
 * TODO: 接入真实鉴权（JWT / Spring Security），X-User-Id 改为从 token 解析
 */
@RestController
public class FlowController {

    private final FlowSubmitService submitService;
    private final ApprovalService approvalService;
    private final AddSignerService addSignerService;
    private final RollbackService rollbackService;
    private final FlowQueryService queryService;
    private final FlowEventQueryService eventQueryService;

    public FlowController(FlowSubmitService submitService, ApprovalService approvalService,
                          AddSignerService addSignerService, RollbackService rollbackService,
                          FlowQueryService queryService, FlowEventQueryService eventQueryService) {
        this.submitService = submitService;
        this.approvalService = approvalService;
        this.addSignerService = addSignerService;
        this.rollbackService = rollbackService;
        this.queryService = queryService;
        this.eventQueryService = eventQueryService;
    }

    @PostMapping("/api/flows")
    @ResponseStatus(HttpStatus.CREATED)
    public FlowInstanceView submit(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody SubmitRequest request) {
        return submitService.submit(request.templateKey(), userId, request.form());
    }

    @GetMapping("/api/flows/{id}")
    public FlowInstanceView get(@PathVariable String id) {
        return queryService.getView(id);
    }

    @PostMapping("/api/flows/{id}/approve")
    public FlowInstanceView approve(@PathVariable String id,
                                    @RequestHeader("X-User-Id") String userId,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @Valid @RequestBody ApproveRequest request) {
        return approvalService.approve(new ApproveCommand(id, userId, request.action(), request.comment(),
                request.expectedVersion(), idempotencyKey));
    }

    @PostMapping("/api/flows/{id}/signers")
    public FlowInstanceView addSigner(@PathVariable String id, @RequestHeader("X-User-Id") String userId,
                                      @Valid @RequestBody AddSignerRequest request) {
        return addSignerService.addSigner(new AddSignerCommand(id, userId, request.approverId(), request.expectedVersion()));
    }

    @PostMapping("/api/flows/{id}/rollback")
    public FlowInstanceView rollback(@PathVariable String id, @RequestHeader("X-User-Id") String userId,
                                     @Valid @RequestBody RollbackRequest request) {
        return rollbackService.rollback(new RollbackCommand(id, userId, request.targetNodeId(), request.reason(),
                request.expectedVersion()));
    }

    /** 断线补拉：客户端带 lastSeq 来拿漏掉的事件。 */
    @GetMapping("/api/flow-events")
    public EventPage events(@RequestParam(defaultValue = "0") long afterSeq,
                            @RequestParam(required = false) String instanceId,
                            @RequestParam(defaultValue = "200") int limit) {
        return eventQueryService.eventsAfter(afterSeq, instanceId, limit);
    }

    // TODO: GET /api/todos            我的待办列表
    // TODO: PUT /api/templates/{key}  发布新版本模板（演示快照隔离）
    // TODO: GET /api/admin/compensations?status=DEAD  + 手动重试补偿任务
}
