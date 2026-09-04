package com.tr.lwsm.obj;

import com.tr.lwsm.obj.entity.TransitionResult;
import com.tr.lwsm.LwsmEvent;
import com.tr.lwsm.LwsmState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认状态机引擎
 *
 * @param <S> 状态
 * @param <E> 事件
 */
public class LwsmStateMachine<S extends LwsmState, E extends LwsmEvent> {

    private final Map<String, String> routeTable = new ConcurrentHashMap<>();

    /**
     * 注册单条路由(支持链式)
     */
    public LwsmStateMachine<S, E> register(S source, E LwsmEvent, S target) {
        String key = source.name() + "_" + LwsmEvent.name();
        routeTable.put(key, target.name());
        return this;
    }

    /**
     * 批量注册
     */
    public LwsmStateMachine<S, E> registerAll(Map<String, String> routes) {
        routeTable.putAll(routes);
        return this;
    }

    /**
     * 转换
     */
    public TransitionResult<S, E> transition(S current, E event) {
        String key = current.name() + "_" + event.name();
        String targetName = routeTable.get(key);

        if (targetName == null) {
            return TransitionResult.fail(current, event, "路由未找到: " + key);
        }

        S target;
        try {
            target = resolveTarget(current, targetName);
        } catch (Exception e) {
            // 解析异常转为 Fail 结果，让调用方统一处理
            return TransitionResult.fail(current, event, "状态解析失败: " + targetName + ", 原因: " + e.getMessage());
        }

        return TransitionResult.success(current, event, target);
    }

    /**
     * 转换，自动抛出异常
     */
    public S transitionWillThrow(S current, E event) {
        TransitionResult<S,E> result = this.transition(current,event);
        if (!result.isSuccess()) {
            // 抛出异常
            throw new IllegalStateException(
                    String.format("状态流转失败: [%s] 状态下无法处理事件 [%s]",
                            current, event)
            );
        }
        return result.getTarget();
    }

    /**
     * 状态解析：
     */
    private S resolveTarget(S current, String targetName) {
        try {
            Class<?> clazz = current.getClass();
            if (!clazz.isEnum()) {
                throw new IllegalArgumentException(
                        "默认解析器要求 S 为枚举类型，但传入的是: " + clazz.getName() +
                                "。请使用 new LwsmStateMachine<>(YourLwsmState::valueOf) 传入自定义解析器。"
                );
            }

            // 检查是否是枚举
            Class<? extends Enum> enumClass = clazz.asSubclass(Enum.class);
            @SuppressWarnings("unchecked")
            S result = (S) Enum.valueOf(enumClass, targetName);
            return result;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "枚举解析失败: " + targetName +
                            "。请确认枚举 " + current.getClass().getSimpleName() + " 中存在该常量，或传入自定义 LwsmStateResolver。",
                    e
            );
        }
    }
}