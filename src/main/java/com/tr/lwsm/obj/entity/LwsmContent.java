package com.tr.lwsm.obj.entity;

/**
 * 公共流转上下文基类。
 * 统一承载“事件”和“业务上下文”，作为 guard/action 的唯一入参，
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 */
public class LwsmContent<E, C> {

    private final E event;
    private final C content;

    public LwsmContent(E event, C content) {
        this.event = event;
        this.content = content;
    }

    /**
     * 当前事件。
     */
    public E event() {
        return event;
    }

    /**
     * 业务上下文。
     */
    public C content() {
        return content;
    }

    public E getEvent() {
        return event;
    }

    public C getContent() {
        return content;
    }
}