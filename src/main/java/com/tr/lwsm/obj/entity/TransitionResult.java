package com.tr.lwsm.obj.entity;

/**
 * 转换结果
 *
 * @param <S> 状态
 * @param <E> 事件
 */
public class TransitionResult<S, E> {

    /**
     * 失败原因。
     */
    public enum FailReason {
        /** 路由不存在 */
        NO_ROUTE,
        /** 路由存在，但所有 guard 均未通过 */
        GUARD_REJECTED
    }

    private final boolean success;
    private final S source;
    private final E event;
    private final S target;
    private final FailReason failReason;
    private final String errorMsg;

    private TransitionResult(boolean success,
                             S source,
                             E event,
                             S target,
                             FailReason failReason,
                             String errorMsg) {
        this.success = success;
        this.source = source;
        this.event = event;
        this.target = target;
        this.failReason = failReason;
        this.errorMsg = errorMsg;
    }

    public static <S, E> TransitionResult<S, E> success(S source, E event, S target) {
        return new TransitionResult<>(true, source, event, target, null, null);
    }

    public static <S, E> TransitionResult<S, E> fail(S source, E event, String errorMsg) {
        return fail(source, event, FailReason.NO_ROUTE, errorMsg);
    }

    public static <S, E> TransitionResult<S, E> fail(
            S source,
            E event,
            FailReason failReason,
            String errorMsg) {
        return new TransitionResult<>(false, source, event, null, failReason, errorMsg);
    }

    public boolean isSuccess() { return success; }
    public S getSource() { return source; }
    public E getEvent() { return event; }
    public S getTarget() { return target; }
    public FailReason getFailReason() { return failReason; }
    public String getErrorMsg() { return errorMsg; }
}