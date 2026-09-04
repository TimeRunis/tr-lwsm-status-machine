package com.tr.lwsm.obj;

import com.tr.lwsm.obj.entity.TransitionResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * 轻量级状态机引擎。
 * <p>
 * 支持任意状态/事件/上下文类型，通过 DSL 注册路由：
 * <pre>{@code
 * engine
 *     .from(OrderState.PENDING)
 *     .on(OrderEvent.PAY)
 *     .to(OrderState.PAYING)
 *     .guard((context, event) -> ...)
 *     .action((context, event) -> ...);
 * }</pre>
 * 同一个 {@code (source, event)} 支持注册多条 transition，
 * 执行时按注册顺序匹配，第一个 guard 通过的 transition 生效。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 */
public class LwsmStateMachine<S, E, C> {

    private final Map<S, Map<E, List<Transition<S, E, C>>>> routes = new ConcurrentHashMap<>();

    // ==================== DSL 入口 ====================

    public FromBuilder from(S source) {
        return new FromBuilder(source);
    }

    // ==================== 简单注册 ====================

    public LwsmStateMachine<S, E, C> register(S source, E event, S target) {
        return register(source, event, target, null, null);
    }

    public LwsmStateMachine<S, E, C> register(
            S source,
            E event,
            S target,
            BiPredicate<C, E> guard,
            BiConsumer<C, E> action) {
        add(source, event, target, guard, action);
        return this;
    }

    // ==================== 流转 ====================

    public TransitionResult<S, E> transition(S current, E event, C context) {
        return doTransition(current, event, context, false);
    }

    public TransitionResult<S, E> transition(S current, E event) {
        return transition(current, event, null);
    }

    public TransitionResult<S, E> fire(S current, E event, C context) {
        return doTransition(current, event, context, true);
    }

    public TransitionResult<S, E> fire(S current, E event) {
        return fire(current, event, null);
    }

    public S transitionWillThrow(S current, E event, C context) {
        TransitionResult<S, E> result = transition(current, event, context);
        if (!result.isSuccess()) {
            throw new IllegalStateException(String.format(
                    "状态流转失败: [%s] 状态下无法处理事件 [%s], 原因: %s",
                    current, event, result.getErrorMsg()));
        }
        return result.getTarget();
    }

    public S transitionWillThrow(S current, E event) {
        return transitionWillThrow(current, event, null);
    }

    // ==================== 内部实现 ====================

    private void add(
            S source,
            E event,
            S target,
            BiPredicate<C, E> guard,
            BiConsumer<C, E> action) {
        routes.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
              .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
              .add(new Transition<>(target, guard, action));
    }

    private TransitionResult<S, E> doTransition(
            S current,
            E event,
            C context,
            boolean runAction) {

        Map<E, List<Transition<S, E, C>>> byEvent = routes.get(current);
        if (byEvent == null) {
            return TransitionResult.fail(current, event,
                    TransitionResult.FailReason.NO_ROUTE,
                    "当前状态无任何路由: " + current);
        }

        List<Transition<S, E, C>> transitions = byEvent.get(event);
        if (transitions == null || transitions.isEmpty()) {
            return TransitionResult.fail(current, event,
                    TransitionResult.FailReason.NO_ROUTE,
                    "路由未找到: " + current + " -> " + event);
        }

        for (Transition<S, E, C> transition : transitions) {
            if (transition.guard == null || transition.guard.test(context, event)) {
                if (runAction && transition.action != null) {
                    transition.action.accept(context, event);
                }
                return TransitionResult.success(current, event, transition.target);
            }
        }

        return TransitionResult.fail(current, event,
                TransitionResult.FailReason.GUARD_REJECTED,
                "所有守卫未通过: " + current + " -> " + event);
    }

    // ==================== 路由定义 ====================

    private static class Transition<S, E, C> {
        final S target;
        final BiPredicate<C, E> guard;
        final BiConsumer<C, E> action;

        Transition(S target,
                   BiPredicate<C, E> guard,
                   BiConsumer<C, E> action) {
            this.target = target;
            this.guard = guard;
            this.action = action;
        }
    }

    // ==================== Builder ====================

    public class FromBuilder {
        private final S source;

        FromBuilder(S source) {
            this.source = source;
        }

        public OnBuilder on(E event) {
            return new OnBuilder(source, event);
        }
    }

    public class OnBuilder {
        private final S source;
        private final E event;

        OnBuilder(S source, E event) {
            this.source = source;
            this.event = event;
        }

        public ToBuilder to(S target) {
            return new ToBuilder(source, event, target);
        }
    }

    public class ToBuilder {
        private final S source;
        private final E event;
        private final S target;
        private BiPredicate<C, E> guard;

        ToBuilder(S source, E event, S target) {
            this.source = source;
            this.event = event;
            this.target = target;
        }

        public ToBuilder guard(BiPredicate<C, E> guard) {
            this.guard = guard;
            return this;
        }

        public LwsmStateMachine<S, E, C> action(BiConsumer<C, E> action) {
            add(source, event, target, guard, action);
            return LwsmStateMachine.this;
        }

        public LwsmStateMachine<S, E, C> register() {
            add(source, event, target, guard, null);
            return LwsmStateMachine.this;
        }
    }
}