package com.tr.lwsm;

import com.tr.lwsm.obj.LwsmStateMachine;
import com.tr.lwsm.obj.entity.TransitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StateMachine 单元测试
 */
class LwsmStateMachineTest {

    // ---------- 定义测试枚举（实现 State/Event 接口） ----------
    enum TestState implements LwsmState {
        INIT("待支付"),
        PAYING("支付中"),
        PAID("已支付"),
        CANCEL("已取消");

        private final String desc;

        TestState(String desc) {
            this.desc = desc;
        }

        @Override
        public String desc() {
            return desc;
        }
    }

    enum TestEvent implements LwsmEvent {
        PAY("发起支付"),
        CANCEL("取消订单"),
        TIMEOUT("超时关闭");

        private final String desc;

        TestEvent(String desc) {
            this.desc = desc;
        }

        @Override
        public String desc() {
            return desc;
        }
    }

    // ---------- 每个测试前重置引擎 ----------
    private LwsmStateMachine<TestState, TestEvent> engine;

    @BeforeEach
    void setUp() {
        engine = new LwsmStateMachine<>();
        engine.register(TestState.INIT, TestEvent.PAY, TestState.PAYING)
                .register(TestState.INIT, TestEvent.CANCEL, TestState.CANCEL)
                .register(TestState.PAYING, TestEvent.PAY, TestState.PAID);
    }

    // ==================== 1. 正常流转 ====================
    @Test
    void shouldTransitionFromInitToPayingWhenPay() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSource()).isEqualTo(TestState.INIT);
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
        assertThat(result.getErrorMsg()).isNull();
    }

    @Test
    void shouldTransitionFromPayingToPaidWhenPayAgain() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.PAYING, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAID);
    }

    @Test
    void shouldTransitionFromInitToCancelWhenCancel() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.INIT, TestEvent.CANCEL);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.CANCEL);
    }

    // ==================== 2. 非法流转（路由不存在） ====================
    @Test
    void shouldFailWhenRouteNotFound() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.INIT, TestEvent.TIMEOUT);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getTarget()).isNull();
        assertThat(result.getErrorMsg()).contains("路由未找到");
    }

    @Test
    void shouldFailWhenCancelOnPayingNotAllowed() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.PAYING, TestEvent.CANCEL);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("路由未找到");
    }

    // ==================== 3. 描述字段 ====================
    @Test
    void shouldReturnCorrectDesc() {
        assertThat(TestState.INIT.getDesc()).isEqualTo("待支付");
        assertThat(TestState.PAID.getDesc()).isEqualTo("已支付");
        assertThat(TestEvent.PAY.getDesc()).isEqualTo("发起支付");
    }

    @Test
    void shouldPreserveDescInResult() {
        TransitionResult<TestState, TestEvent> result = engine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.getSource().getDesc()).isEqualTo("待支付");
        assertThat(result.getTarget().getDesc()).isEqualTo("支付中");
    }

    // ==================== 4. 批量注册 ====================
    @Test
    void batchRegisterShouldWork() {
        Map<String, String> routes = new HashMap<>();
        routes.put("INIT_PAY", "PAYING");
        routes.put("PAYING_PAY", "PAID");

        LwsmStateMachine<TestState, TestEvent> batchEngine = new LwsmStateMachine<>();
        batchEngine.registerAll(routes);

        TransitionResult<TestState, TestEvent> result = batchEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
    }

    // ==================== 5. 连续流转 ====================
    @Test
    void shouldSupportFullPaymentFlow() {
        LwsmStateMachine<TestState, TestEvent> flowEngine = new LwsmStateMachine<>();
        flowEngine.register(TestState.INIT, TestEvent.PAY, TestState.PAYING)
                .register(TestState.PAYING, TestEvent.PAY, TestState.PAID);

        TransitionResult<TestState, TestEvent> r1 = flowEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(r1.getTarget()).isEqualTo(TestState.PAYING);

        TransitionResult<TestState, TestEvent> r2 = flowEngine.transition(TestState.PAYING, TestEvent.PAY);
        assertThat(r2.getTarget()).isEqualTo(TestState.PAID);
    }

    // ==================== 6. 链式调用 ====================
    @Test
    void chainedRegisterShouldBeReadable() {
        LwsmStateMachine<TestState, TestEvent> chainEngine = new LwsmStateMachine<>();
        chainEngine.register(TestState.INIT, TestEvent.PAY, TestState.PAYING)
                .register(TestState.PAYING, TestEvent.PAY, TestState.PAID);

        TransitionResult<TestState, TestEvent> result = chainEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
    }
}