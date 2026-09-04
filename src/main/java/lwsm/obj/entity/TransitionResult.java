package lwsm.obj.entity;


import lwsm.LwsmEvent;
import lwsm.LwsmState;

/**
 * 转换结果
 * @param <S> 状态
 * @param <E> 事件
 */
public class TransitionResult<S extends LwsmState, E extends LwsmEvent> {
    private final boolean success;
    private final S source;
    private final E event;
    private final S target;
    private final String errorMsg;

    private TransitionResult(boolean success, S source, E event, S target, String errorMsg) {
        this.success = success;
        this.source = source;
        this.event = event;
        this.target = target;
        this.errorMsg = errorMsg;
    }

    public static <S extends LwsmState, E extends LwsmEvent> TransitionResult<S, E> success(S source, E event, S target) {
        return new TransitionResult<>(true, source, event, target, null);
    }

    public static <S extends LwsmState, E extends LwsmEvent> TransitionResult<S, E> fail(S source, E event, String errorMsg) {
        return new TransitionResult<>(false, source, event, null, errorMsg);
    }

    public boolean isSuccess() { return success; }
    public S getSource() { return source; }
    public E getEvent() { return event; }
    public S getTarget() { return target; }
    public String getErrorMsg() { return errorMsg; }
}