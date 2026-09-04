package com.tr.lwsm.obj;

import com.tr.lwsm.obj.entity.LwsmContent;
import com.tr.lwsm.obj.entity.TransitionResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 轻量级状态机引擎。
 *
 * 支持任意状态/事件/上下文类型，通过 DSL 注册路由：
 * <pre>{@code
 * engine
 *     .from(OrderState.PENDING)
 *     .on(OrderEvent.PAY)
 *     .to(OrderState.PAYING)
 *     .guard(content -> ...)
 *     .action(content -> ...);
 * }</pre>
 * 实际 guard/action 入参统一为 {@link LwsmContent}，内部同时携带 event 和业务上下文。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 */
public class LwsmStateMachine<S, E, C> {

    private final Map<S, Map<E, Transition<S, E, C>>> routes = new ConcurrentHashMap<>();

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
            Predicate<LwsmContent<E, C>> guard,
            Consumer<LwsmContent<E, C>> action) {
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
            Predicate<LwsmContent<E, C>> guard,
            Consumer<LwsmContent<E, C>> action) {
        routes.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
              .put(event, new Transition<>(target, guard, action));
    }

    private TransitionResult<S, E> doTransition(
            S current,
            E event,
            C context,
            boolean runAction) {

        Map<E, Transition<S, E, C>> byEvent = routes.get(current);
        if (byEvent == null) {
            return TransitionResult.fail(current, event, "当前状态无任何路由: " + current);
        }

        Transition<S, E, C> transition = byEvent.get(event);
        if (transition == null) {
            return TransitionResult.fail(current, event,
                    "路由未找到: " + current + " -> " + event);
        }

        LwsmContent<E, C> lwsmContent = new LwsmContent<>(event, context);

        if (transition.guard != null && !transition.guard.test(lwsmContent)) {
            return TransitionResult.fail(current, event,
                    "守卫未通过: " + current + " -> " + event);
        }

        if (runAction && transition.action != null) {
            transition.action.accept(lwsmContent);
        }

        return TransitionResult.success(current, event, transition.target);
    }

    // ==================== 路由定义 ====================

    private static class Transition<S, E, C> {
        final S target;
        final Predicate<LwsmContent<E, C>> guard;
        final Consumer<LwsmContent<E, C>> action;

        Transition(S target,
                   Predicate<LwsmContent<E, C>> guard,
                   Consumer<LwsmContent<E, C>> action) {
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
        private Predicate<LwsmContent<E, C>> guard;

        ToBuilder(S source, E event, S target) {
            this.source = source;
            this.event = event;
            this.target = target;
        }

        public ToBuilder guard(Predicate<LwsmContent<E, C>> guard) {
            this.guard = guard;
            return this;
        }

        public LwsmStateMachine<S, E, C> action(Consumer<LwsmContent<E, C>> action) {
            add(source, event, target, guard, action);
            return LwsmStateMachine.this;
        }

        public LwsmStateMachine<S, E, C> register() {
            add(source, event, target, guard, null);
            return LwsmStateMachine.this;
        }
    }
}