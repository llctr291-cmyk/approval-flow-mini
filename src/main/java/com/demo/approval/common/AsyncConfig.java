package com.demo.approval.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /** 补偿任务执行器：Java 21 虚拟线程，补偿动作多为 IO（撤待办、发通知），不占平台线程。 */
    @Bean(name = "compensationExecutorService", destroyMethod = "close")
    public ExecutorService compensationExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** 独立事务模板：补偿步骤失败不能污染外层事务（否则外层记录失败状态也会被回滚）。 */
    @Bean(name = "requiresNewTransactionTemplate")
    public TransactionTemplate requiresNewTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
