package com.demo.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 多级审批流原型入口。
 *
 * <pre>
 * 用户点“同意”
 *   → 幂等检查 → 权限检查 → 版本号检查
 *   → 记录投票 → 会签/或签判断（过 / 拒 / 继续等）
 *   → CAS 写库（version + 1，并发只有一个人成功）
 *   → 写事件日志（seq 递增） → WebSocket 推送 patch
 *   → 客户端：version 更旧就丢弃；断线重连按 lastSeq 补拉
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
public class ApprovalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}
