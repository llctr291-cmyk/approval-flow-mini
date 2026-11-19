package com.demo.approval.compensation;

import com.demo.approval.service.NotificationService;
import org.springframework.stereotype.Component;

/**
 * 通知被跳过的审批人。通知本身做不到严格幂等（重试可能重复发），
 * 这里接受"至少一次"，由消息端按 instanceId + round 去重。
 */
@Component
public class NotifyApproversStep implements CompensationStep {

    public static final String NAME = "NOTIFY_APPROVERS";

    private final NotificationService notificationService;

    public NotifyApproversStep(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(CompensationContext context) {
        notificationService.notifyUsers(context.stringList("userIds"), context.stringValue("message"));
    }
}
