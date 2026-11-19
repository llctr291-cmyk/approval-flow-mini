# approval-flow-mini
多级审批流原型（Spring Boot 3 / Java 21）：幂等 + CAS 乐观锁 + 会签/或签并发收敛 + 动态加签 + 跨节点回退补偿 + 事件游标实时同步

## 启动

```bash
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn spring-boot:run
```

核心能力：

- 创建实例时按节点生成审批任务；节点支持 `ALL` 会签和 `ANY` 或签。
- 所有写操作要求幂等键，相同幂等键返回相同结果；不同操作复用幂等键会被拒绝。
- 实例版本号作为 CAS 校验值，回退时携带期望版本，版本不匹配抛出冲突。
- 动态加签只会为目标节点增加未重复的待审批任务。
- 跨节点回退会恢复目标节点及后续节点的待审批任务，并追加补偿事件。
- 事件按全局单调游标追加；客户端可通过 `cursor` 获取增量事件。

主要接口：

- `POST /api/approvals`
- `POST /api/approvals/{instanceId}/tasks/{taskId}/actions`
- `POST /api/approvals/{instanceId}/nodes/{nodeId}/add-sign`
- `POST /api/approvals/{instanceId}/rollback`
- `GET /api/approvals/{instanceId}/events?cursor=0`
