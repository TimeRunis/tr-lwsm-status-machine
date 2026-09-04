package com.tr.lwsm;

import com.tr.lwsm.obj.CustomStateMachine;
import com.tr.lwsm.obj.entity.CustomTransitionResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomStateMachine单元测试
 */
class CustomStateMachineTest {

    // ==================== 1.字符串作为状态/事件 ====================
    @Test
    void shouldWorkWithStringStateAndEvent() {
        CustomStateMachine<String, String> engine = new CustomStateMachine<>(Function.identity());
        engine.register("INIT", "PAY", "PAYING")
              .register("PAYING", "PAY", "PAID");

        CustomTransitionResult<String, String> result = engine.transition("INIT", "PAY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSource()).isEqualTo("INIT");
        assertThat(result.getTarget()).isEqualTo("PAYING");
    }

    @Test
    void shouldFailWhenStringRouteNotFound() {
        CustomStateMachine<String, String> engine = new CustomStateMachine<>(Function.identity());
        engine.register("INIT", "PAY", "PAYING");

        CustomTransitionResult<String, String> result = engine.transition("INIT", "CANCEL");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("路由未找到");
    }

    // ==================== 2. POJO 作为状态/事件 ====================
    static class MyState {
        private final String code;
        private final String desc;

        MyState(String code,String desc) {
            this.code = code;
            this.desc = desc;
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
        private final String desc;

        MyEvent(String code,String desc) {
            this.code = code;
            this.desc = desc;
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
        Map<String, MyState> stateCache = new HashMap<>();
        stateCache.put("INIT", new MyState("INIT","待支付"));
        stateCache.put("PAYING", new MyState("PAYING","支付中"));
        stateCache.put("PAID", new MyState("PAID","已支付"));

        CustomStateMachine<MyState, MyEvent> engine = new CustomStateMachine<>(stateCache::get);

        MyState init = new MyState("INIT","待支付");
        MyEvent pay = new MyEvent("PAY","去支付");
        MyState paying = new MyState("PAYING","已支付");

        engine.register(init, pay, paying);

        CustomTransitionResult<MyState, MyEvent> result = engine.transition(init, pay);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(paying);
    }

    @Test
    void shouldFailWhenPojoResolverReturnsNull() {
        CustomStateMachine<MyState, MyEvent> engine = new CustomStateMachine<>(name -> null);
        engine.register(new MyState("INIT","待支付"), new MyEvent("PAY","去支付"), new MyState("PAYING","支付中"));

        CustomTransitionResult<MyState, MyEvent> result = engine.transition(new MyState("INIT","待支付"), new MyEvent("PAY","已支付"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("返回 null");
    }

    // ==================== 场景3：枚举但不用 State 接口 ====================
    enum PlainState { INIT, PAYING, PAID }
    enum PlainEvent { PAY, CANCEL }

    @Test
    void shouldWorkWithPlainEnumWithoutStateInterface() {
        CustomStateMachine<PlainState, PlainEvent> engine = new CustomStateMachine<>(PlainState::valueOf);

        engine.register(PlainState.INIT, PlainEvent.PAY, PlainState.PAYING);

        CustomTransitionResult<PlainState, PlainEvent> result = engine.transition(PlainState.INIT, PlainEvent.PAY);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo(PlainState.PAYING);
    }

    // ==================== 场景4：批量注册 + 连续流转 ====================
    @Test
    void batchRegisterAndMultipleTransitionsShouldWork() {
        Map<String, String> routes = new HashMap<>();
        routes.put("INIT_PAY", "PAYING");
        routes.put("PAYING_PAY", "PAID");

        CustomStateMachine<String, String> engine = new CustomStateMachine<>(Function.identity());
        engine.registerAll(routes);

        CustomTransitionResult<String, String> r1 = engine.transition("INIT", "PAY");
        assertThat(r1.getTarget()).isEqualTo("PAYING");

        CustomTransitionResult<String, String> r2 = engine.transition("PAYING", "PAY");
        assertThat(r2.getTarget()).isEqualTo("PAID");
    }

    // ==================== 场景5：链式调用 ====================
    @Test
    void chainedRegisterShouldWork() {
        CustomStateMachine<String, String> engine = new CustomStateMachine<>(Function.identity());
        engine.register("INIT", "PAY", "PAYING")
              .register("PAYING", "PAY", "PAID");

        CustomTransitionResult<String, String> result = engine.transition("INIT", "PAY");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget()).isEqualTo("PAYING");
    }

    // ==================== 场景6：自定义 toString 的 POJO ====================
    static class BadState {
        private final String code;
        BadState(String code) { this.code = code; }
        @Override public String toString() { return "状态_" + code; }  // 加了前缀
    }

    @Test
    void shouldFailWhenToStringNotMatchingResolver() {
        // 路由注册时 key 是 "状态_INIT_事件_PAY"，解析时 name 也是同样的格式
        // 这里只是演示：如果 toString 格式变化，解析器要同步调整
        CustomStateMachine<BadState, String> engine = new CustomStateMachine<>(
            name -> new BadState(name.replace("状态_", ""))  // 需要把前缀剥掉
        );

        BadState init = new BadState("INIT");
        engine.register(init, "PAY", new BadState("PAYING"));

        CustomTransitionResult<BadState, String> result = engine.transition(init, "PAY");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTarget().toString()).isEqualTo("状态_PAYING");
    }
}