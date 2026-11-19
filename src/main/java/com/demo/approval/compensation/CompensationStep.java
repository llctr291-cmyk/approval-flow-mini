package com.demo.approval.compensation;

/**
 * 一个补偿动作。
 *
 * <p><b>实现必须幂等</b>：补偿任务是"至少执行一次"语义，执行成功但标记 DONE 之前进程挂了，
 * 重启后会再执行一遍。所以"撤销待办"要写成 PENDING→CANCELLED 的条件更新，
 * "释放预算"要先判断状态，而不是无脑执行。</p>
 */
public interface CompensationStep {

    /** 和 compensation_task.step_name 对应。 */
    String name();

    void execute(CompensationContext context);
}
