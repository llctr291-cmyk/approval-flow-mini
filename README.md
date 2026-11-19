# approval-flow-mini

多级审批流原型（Spring Boot 3 / Java 21）。重点解决两个问题：

1. **动态多级审批的并发正确性**：会签/或签同时投票、动态加签、跨节点回退时，单据状态不乱
2. **状态实时同步**：消息乱序、断网重连时，前端状态不倒退、不漏更新

> 🚧 进行中的原型，部分模块只写了接口和注释，后续通过 Devin 按任务补齐（见下方计划）。

## 核心链路

```
用户点“同意”
   │
   ▼
幂等检查 → 权限检查 → 版本号检查
   │
   ▼
记录投票 → 会签/或签判断（过 / 拒 / 继续等）
   │
   ▼
CAS 写库（version + 1，并发只有一个人成功）
   │
   ▼
写事件日志（seq 递增） → WebSocket 推送 patch
   │
   ▼
客户端：version 更旧就丢弃；断线重连按 lastSeq 补拉
```

## 代码导航

| 概念 | 位置 |
|---|---|
| 审批主流程（幂等 + CAS + 收敛） | `service/ApprovalService.java` |
| CAS 更新语句 | `repository/FlowInstanceRepository#compareAndSet` |
| 会签 / 或签收敛 | `engine/ConvergenceEvaluator.java` |
| 动态路由（SpEL 条件） | `engine/FlowRoutingEngine.java` |
| 流程快照版本隔离 | `domain/entity/FlowInstance#snapshotJson` |
| 动态加签 | `service/AddSignerService.java` |
| 跨节点回退 + Outbox 补偿 | `service/RollbackService.java`、`compensation/*` |
| 事件日志 + afterCommit 推送 | `sync/FlowEventPublisher.java` |
| 游标补拉（含读延迟窗口） | `sync/FlowEventQueryService.java` |
| WebSocket 推送 | `sync/FlowWebSocketHub.java` |

## 关键设计点

- **为什么"只记一票"也要 CAS**：会签两人同时同意，若不加版本控制，两个事务都只看到自己的票（判定 PENDING），提交后票齐了却没人推进节点，单子永远卡住。每次投票都抢版本号，把并发投票串行化。
- **推送在 afterCommit**：避免事务回滚后客户端看到"从未发生的状态"。
- **WebSocket 只是快速通道**，事件表才是真相来源；推送丢了靠 seq 补拉。
- **补偿步骤必须幂等**：补偿任务是"至少执行一次"。

## 运行

```bash
mvn spring-boot:run
# 提交一张 8000 元的报销
curl -X POST localhost:8080/api/flows -H "X-User-Id: emp_01" -H "Content-Type: application/json" \
  -d '{"templateKey":"expense","form":{"amount":8000}}'
# 领导同意（expectedVersion 填上一步返回的 version）
curl -X POST localhost:8080/api/flows/{id}/approve -H "X-User-Id: leader_zhang" -H "Idempotency-Key: k1" \
  -H "Content-Type: application/json" -d '{"action":"AGREE","expectedVersion":1}'
```

## Devin 协同计划（TODO）

| # | 任务 | 验收标准 |
|---|---|---|
| 1 | 并发会签/或签复现测试：`CountDownLatch` 让 N 人同时审批 | 节点只推进一次、待办不重复 |
| 2 | 并发冲突自动重试 + 压测报告 | 1000 轮并发 0 卡单 |
| 3 | 补偿链路混沌测试（`flow.notify.failure-rate`） | 失败自动重试，超限进 DEAD 队列 |
| 4 | 前端同步客户端：version 去重 + lastSeq 补拉 + 乱序注入测试 | 断网重连不漏不倒退 |
| 5 | 待办 / 模板发布 / 补偿管理接口 | 见 `FlowController` 中的 TODO |
| 6 | 接入真实鉴权，替换 `X-User-Id` | — |
