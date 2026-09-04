package com.tr.lwsm;

import com.tr.lwsm.obj.LwsmStateMachine;
import com.tr.lwsm.obj.entity.TransitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LwsmStateMachine 单元测试
 */
class LwsmStateMachineTest {

    // ---------- 定义测试枚举 ----------
    enum TestState {
        INIT, PAYING, PAID, CANCEL
    }

    enum TestEvent {
        PAY, CANCEL, TIMEOUT
    }

    // ---------- 每个测试前重置引擎 ----------
    private LwsmStateMachine<TestState, TestEvent, Void> engine;

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

    // ==================== 3. 批量注册 ====================
    @Test
    void batchRegisterShouldWork() {
        LwsmStateMachine<TestState, TestEvent, Void> batchEngine = new LwsmStateMachine<>();
        // 新引擎不依赖字符串路由表，这里改为直接注册路由
        batchEngine.register(TestState.INIT, TestEvent.PAY, TestState.PAYING)
                .register(TestState.PAYING, TestEvent.PAY, TestState.PAID);

        TransitionResult<TestState, TestEvent> result = batchEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
    }

    // ==================== 4. 连续流转 ====================
    @Test
    void shouldSupportFullPaymentFlow() {
        LwsmStateMachine<TestState, TestEvent, Void> flowEngine = new LwsmStateMachine<>();
        flowEngine.register(TestState.INIT, TestEvent.PAY, TestState.PAYING)
                .register(TestState.PAYING, TestEvent.PAY, TestState.PAID);

        TransitionResult<TestState, TestEvent> r1 = flowEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(r1.getTarget()).isEqualTo(TestState.PAYING);

        TransitionResult<TestState, TestEvent> r2 = flowEngine.transition(TestState.PAYING, TestEvent.PAY);
        assertThat(r2.getTarget()).isEqualTo(TestState.PAID);
    }

    // ==================== 5. 链式注册 ====================
    @Test
    void chainedRegisterShouldBeReadable() {
        LwsmStateMachine<TestState, TestEvent, Void> chainEngine = new LwsmStateMachine<>();
        chainEngine.from(TestState.INIT)
                .on(TestEvent.PAY)
                .to(TestState.PAYING)
                .register();

        TransitionResult<TestState, TestEvent> result = chainEngine.transition(TestState.INIT, TestEvent.PAY);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
    }

    // ==================== 6. DSL + guard/action ====================
    @Test
    void shouldSupportDslWithGuardAndAction() {
        LwsmStateMachine<TestState, TestEvent, Void> dslEngine = new LwsmStateMachine<>();
        boolean[] actionCalled = {false};

        dslEngine
                .from(TestState.INIT)
                .on(TestEvent.PAY)
                .to(TestState.PAYING)
                .guard(content -> true)
                .action(content -> actionCalled[0] = true);

        TransitionResult<TestState, TestEvent> result = dslEngine.fire(TestState.INIT, TestEvent.PAY);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(TestState.PAYING);
        assertThat(actionCalled[0]).isTrue();
    }

    @Test
    void shouldBlockWhenGuardRejected() {
        LwsmStateMachine<TestState, TestEvent, Void> dslEngine = new LwsmStateMachine<>();

        dslEngine
                .from(TestState.INIT)
                .on(TestEvent.PAY)
                .to(TestState.PAYING)
                .guard(content -> false)
                .register();

        TransitionResult<TestState, TestEvent> result = dslEngine.fire(TestState.INIT, TestEvent.PAY);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("守卫未通过");
    }

    @Test
    void transitionShouldNotRunAction() {
        LwsmStateMachine<TestState, TestEvent, Void> dslEngine = new LwsmStateMachine<>();
        boolean[] actionCalled = {false};

        dslEngine
                .from(TestState.INIT)
                .on(TestEvent.PAY)
                .to(TestState.PAYING)
                .action(content -> actionCalled[0] = true);

        TransitionResult<TestState, TestEvent> result = dslEngine.transition(TestState.INIT, TestEvent.PAY);

        assertThat(result.isSuccess()).isTrue();
        assertThat(actionCalled[0]).isFalse();
    }

    // ==================== 7. transitionWillThrow ====================
    @Test
    void transitionWillThrowShouldThrowWhenFail() {
        assertThatThrownBy(() -> engine.transitionWillThrow(TestState.PAYING, TestEvent.CANCEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("路由未找到");
    }
}