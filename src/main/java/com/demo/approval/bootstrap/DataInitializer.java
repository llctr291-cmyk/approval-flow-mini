package com.demo.approval.bootstrap;

import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.domain.model.FlowTransition;
import com.demo.approval.domain.model.SignMode;
import com.demo.approval.service.TemplateService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时初始化一个"报销审批"模板：
 * <pre>
 * leader（或签：张/李任一同意）
 *   ├─ amount > 5000  → manager（会签：经理 + 财务都同意）
 *   │                      ├─ amount > 20000 → boss（老板）
 *   │                      └─ 否则结束
 *   └─ 否则结束
 * </pre>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final TemplateService templateService;

    public DataInitializer(TemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public void run(String... args) {
        if (templateService.exists("expense")) {
            return;
        }
        FlowDefinition definition = new FlowDefinition("报销审批", "leader", List.of(
                new FlowNodeDefinition("leader", "直属领导审批", SignMode.ANY,
                        List.of("leader_zhang", "leader_li"),
                        List.of(new FlowTransition("#form['amount'] > 5000", "manager"),
                                new FlowTransition(null, null))),
                new FlowNodeDefinition("manager", "经理 + 财务会签", SignMode.ALL,
                        List.of("manager_wang", "finance_zhao"),
                        List.of(new FlowTransition("#form['amount'] > 20000", "boss"),
                                new FlowTransition(null, null))),
                new FlowNodeDefinition("boss", "老板审批", SignMode.ANY,
                        List.of("boss_chen"),
                        List.of())));
        templateService.publish("expense", definition, "system");
    }
}
