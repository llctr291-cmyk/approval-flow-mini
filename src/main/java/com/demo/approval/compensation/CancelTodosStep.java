package com.demo.approval.compensation;

import com.demo.approval.service.TodoService;
import org.springframework.stereotype.Component;

/** 撤销旧轮次所有未处理的待办。条件更新，天然幂等。 */
@Component
public class CancelTodosStep implements CompensationStep {

    public static final String NAME = "CANCEL_TODOS";

    private final TodoService todoService;

    public CancelTodosStep(TodoService todoService) {
        this.todoService = todoService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(CompensationContext context) {
        todoService.cancelRound(context.instanceId(), context.intValue("roundNo"));
    }
}
