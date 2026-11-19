package com.demo.approval.compensation;

import com.demo.approval.service.BudgetService;
import org.springframework.stereotype.Component;

/** 回退到起点时释放预算占用。BudgetLock.release() 只在 LOCKED 时生效，幂等。 */
@Component
public class ReleaseBudgetStep implements CompensationStep {

    public static final String NAME = "RELEASE_BUDGET";

    private final BudgetService budgetService;

    public ReleaseBudgetStep(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(CompensationContext context) {
        budgetService.release(context.instanceId());
    }
}
