package com.tr.lwsm;

public interface LwsmEvent {
    /**
     * 唯一标识
     */
    String name();

    /**
     * 描述文本
     */
    String desc();

    default String getDesc(){
        return desc();
    }
}
