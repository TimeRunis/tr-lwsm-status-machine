package lwsm.obj.entity;

/**
 * 自定义转换结果
 * @param <S>
 * @param <E>
 */
public class CustomTransitionResult<S, E> {
    private final boolean success;
    private final S source;
    private final E event;
    private final S target;
    private final String errorMsg;

    private CustomTransitionResult(boolean success, S source, E event, S target, String errorMsg) {
        this.success = success;
        this.source = source;
        this.event = event;
        this.target = target;
        this.errorMsg = errorMsg;
    }

    public static <S, E> CustomTransitionResult<S, E> success(S source, E event, S target) {
        return new CustomTransitionResult<>(true, source, event, target, null);
    }

    public static <S, E>CustomTransitionResult<S, E> fail(S source, E event, String errorMsg) {
        return new CustomTransitionResult<>(false, source, event, null, errorMsg);
    }

    public boolean isSuccess() { return success; }
    public S getSource() { return source; }
    public E getEvent() { return event; }
    public S getTarget() { return target; }
    public String getErrorMsg() { return errorMsg; }
}