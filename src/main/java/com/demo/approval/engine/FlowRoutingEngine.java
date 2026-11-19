package com.demo.approval.engine;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.domain.model.FlowTransition;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态流程编排：节点通过后根据表单数据决定下一站。
 *
 * <p>条件表达式使用 SpEL + {@link SimpleEvaluationContext}（只读数据绑定），
 * 禁止 {@code T(java.lang.Runtime)} 之类的类型引用和任意方法调用，避免模板配置变成远程代码执行入口。</p>
 *
 * <p>匹配顺序：先按声明顺序匹配带条件的出口，都不满足再走默认出口；没有默认出口则流程结束。</p>
 */
@Component
public class FlowRoutingEngine {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /** @return 下一个节点 id；Optional.empty() 表示流程结束 */
    public Optional<String> resolveNext(FlowNodeDefinition node, Map<String, Object> form) {
        FlowTransition defaultTransition = null;
        for (FlowTransition transition : node.transitions()) {
            if (transition.isDefault()) {
                defaultTransition = transition;
                continue;
            }
            if (matches(transition.condition(), form)) {
                return targetOf(transition);
            }
        }
        return defaultTransition == null ? Optional.empty() : targetOf(defaultTransition);
    }

    /** 发布模板时预编译所有条件，语法错误在发布阶段就暴露，而不是跑到一半才炸。 */
    public void precompile(String condition) {
        compile(condition);
    }

    private Optional<String> targetOf(FlowTransition transition) {
        return transition.endsFlow() ? Optional.empty() : Optional.of(transition.targetNodeId());
    }

    private boolean matches(String condition, Map<String, Object> form) {
        Expression expression = compile(condition);
        EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("form", form);
        try {
            return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
        } catch (EvaluationException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "路由条件计算失败: " + condition + "，原因: " + e.getMessage());
        }
    }

    private Expression compile(String condition) {
        try {
            return expressionCache.computeIfAbsent(condition, parser::parseExpression);
        } catch (ParseException e) {
            throw new BusinessException(ErrorCode.INVALID_DEFINITION, "路由条件语法错误: " + condition);
        }
    }
}
