package com.tr.lwsm;

import com.tr.lwsm.obj.LwsmStateMachine;
import com.tr.lwsm.obj.entity.TransitionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义类型场景测试：字符串、POJO、普通枚举、上下文参数。
 */
class LwsmStateMachineCustomTypeTest {

    // ==================== 1.字符串作为状态/事件 ====================
    @Test
    void shouldWorkWithStringStateAndEvent() {
        LwsmStateMachine<String, String, Void> engine = new LwsmStateMachine<>();
        engine.register("INIT", "PAY", "PAYING")
              .register("PAYING", "PAY", "PAID");

        TransitionResult<String, String> result = engine.transition("INIT", "PAY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSource()).isEqualTo("INIT");
        assertThat(result.getTarget()).isEqualTo("PAYING");
    }

    @Test
    void shouldFailWhenStringRouteNotFound() {
        LwsmStateMachine<String, String, Void> engine = new LwsmStateMachine<>();
        engine.register("INIT", "PAY", "PAYING");

        TransitionResult<String, String> result = engine.transition("INIT", "CANCEL");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("路由未找到");
    }

    // ==================== 2. POJO 作为状态/事件 ====================
    static class MyState {
        private final String code;

        MyState(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyState myState = (MyState) o;
            return code.equals(myState.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    static class MyEvent {
        private final String code;

        MyEvent(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyEvent myEvent = (MyEvent) o;
            return code.equals(myEvent.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    @Test
    void shouldWorkWithPojoStateAndEvent() {
        MyState init = new MyState("INIT");
        MyState paying = new MyState("PAYING");
        MyEvent pay = new MyEvent("PAY");

        LwsmStateMachine<MyState, MyEvent, Void> engine = new LwsmStateMachine<>();
        engine.register(init, pay, paying);

        TransitionResult<MyState, MyEvent> result = engine.transition(init, pay);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(paying);
    }

    // ==================== 3.枚举但不用 State 接口 ====================
    enum PlainState { INIT, PAYING, PAID }
    enum PlainEvent { PAY, CANCEL }

    @Test
    void shouldWorkWithPlainEnumWithoutStateInterface() {
        LwsmStateMachine<PlainState, PlainEvent, Void> engine = new LwsmStateMachine<>();
        engine.register(PlainState.INIT, PlainEvent.PAY, PlainState.PAYING);

        TransitionResult<PlainState, PlainEvent> result = engine.transition(PlainState.INIT, PlainEvent.PAY);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(PlainState.PAYING);
    }

    // ==================== 4.批量注册 + 连续流转 ====================
    @Test
    void batchRegisterAndMultipleTransitionsShouldWork() {
        LwsmStateMachine<String, String, Void> engine = new LwsmStateMachine<>();
        engine.register("INIT", "PAY", "PAYING")
              .register("PAYING", "PAY", "PAID");

        TransitionResult<String, String> r1 = engine.transition("INIT", "PAY");
        assertThat(r1.getTarget()).isEqualTo("PAYING");

        TransitionResult<String, String> r2 = engine.transition("PAYING", "PAY");
        assertThat(r2.getTarget()).isEqualTo("PAID");
    }

    // ==================== 5. DSL + 上下文参数 ====================
    static class OrderContext {
        private final String orderId;
        private final BigDecimal amount;

        OrderContext(String orderId, BigDecimal amount) {
            this.orderId = orderId;
            this.amount = amount;
        }

        String orderId() {
            return orderId;
        }

        BigDecimal amount() {
            return amount;
        }
    }

    @Test
    void shouldSupportContextWithDsl() {
        LwsmStateMachine<String, String, OrderContext> engine = new LwsmStateMachine<>();

        boolean[] actionCalled = {false};

        engine
                .from("INIT")
                .on("PAY")
                .to("PAYING")
                .guard((ctx, event) ->
                        event.equals("PAY") &&
                        ctx.amount().compareTo(BigDecimal.ZERO) > 0)
                .action((ctx, event) -> {
                    actionCalled[0] = true;
                    assertThat(ctx.orderId()).isEqualTo("O001");
                });

        OrderContext ctx = new OrderContext("O001", new BigDecimal("100"));
        TransitionResult<String, String> result = engine.fire("INIT", "PAY", ctx);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo("PAYING");
        assertThat(actionCalled[0]).isTrue();
    }

    @Test
    void shouldBlockWhenContextGuardRejected() {
        LwsmStateMachine<String, String, OrderContext> engine = new LwsmStateMachine<>();

        engine
                .from("INIT")
                .on("PAY")
                .to("PAYING")
                .guard((ctx, event) -> ctx.amount().compareTo(BigDecimal.ZERO) > 0)
                .register();

        OrderContext ctx = new OrderContext("O002", new BigDecimal("-1"));
        TransitionResult<String, String> result = engine.fire("INIT", "PAY", ctx);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("所有守卫未通过");
        assertThat(result.getFailReason()).isEqualTo(TransitionResult.FailReason.GUARD_REJECTED);
    }
}