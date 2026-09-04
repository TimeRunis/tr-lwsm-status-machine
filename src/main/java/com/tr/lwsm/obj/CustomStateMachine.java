package com.tr.lwsm.obj;

import com.tr.lwsm.obj.entity.CustomTransitionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 自定义状态机引擎
 *
 * @param <S> 状态（任意类型）
 * @param <E> 事件（任意类型）
 */
public class CustomStateMachine<S, E> {

    private final Map<String, String> routeTable = new HashMap<>();
    private final Function<String, S> stateResolver;

    /**
     * 构造器：必须传入状态解析器
     */
    public CustomStateMachine(Function<String, S> stateResolver) {
        this.stateResolver = stateResolver;
    }

    /**
     * 注册单条路由(支持链式)
     */
    public CustomStateMachine<S, E> register(S source, E event, S target) {
        String key = source.toString() + "_" + event.toString();
        routeTable.put(key, target.toString());
        return this;
    }

    /**
     * 批量注册
     */
    public CustomStateMachine<S, E> registerAll(Map<String, String> routes) {
        routeTable.putAll(routes);
        return this;
    }

    /**
     * 转换
     */
    public CustomTransitionResult<S, E> transition(S current, E event) {
        String key = current.toString() + "_" + event.toString();
        String targetName = routeTable.get(key);

        if (targetName == null) {
            return CustomTransitionResult.fail(current, event, "路由未找到: " + key);
        }

        S target;
        try {
            target = stateResolver.apply(targetName);
        } catch (Exception e) {
            return CustomTransitionResult.fail(current, event, "状态解析失败: " + targetName + ", 原因: " + e.getMessage());
        }

        if (target == null) {
            return CustomTransitionResult.fail(current, event, "状态解析返回 null: " + targetName);
        }

        return CustomTransitionResult.success(current, event, target);
    }

    /**
     * 转换，自动抛出异常
     */
    public S transitionWillThrow(S current, E event) {
        CustomTransitionResult<S,E> result = this.transition(current,event);
        if (!result.isSuccess()) {
            // 抛出异常
            throw new IllegalStateException(
                    String.format("状态流转失败: [%s] 状态下无法处理事件 [%s]",
                            current, event)
            );
        }
        return result.getTarget();
    }
}