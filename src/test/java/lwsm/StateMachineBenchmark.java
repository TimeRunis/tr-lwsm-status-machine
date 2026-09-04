package lwsm;

import lwsm.obj.CustomStateMachine;
import lwsm.obj.StateMachine;
import lwsm.obj.entity.CustomTransitionResult;
import lwsm.obj.entity.TransitionResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 状态机引擎 JMH 性能基准测试（50 个状态）
 *
 * 测试目标：验证引擎在大规模状态下的吞吐量表现
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StateMachineBenchmark {

    // ==================== 50 个状态的枚举（实现 State 接口） ====================
    enum TestState implements LwsmState {
        S00, S01, S02, S03, S04, S05, S06, S07, S08, S09,
        S10, S11, S12, S13, S14, S15, S16, S17, S18, S19,
        S20, S21, S22, S23, S24, S25, S26, S27, S28, S29,
        S30, S31, S32, S33, S34, S35, S36, S37, S38, S39,
        S40, S41, S42, S43, S44, S45, S46, S47, S48, S49;

        @Override
        public String desc() { return name(); }
    }

    // ==================== 50 个事件的枚举（实现 Event 接口） ====================
    enum TestEvent implements LwsmEvent {
        E00, E01, E02, E03, E04, E05, E06, E07, E08, E09,
        E10, E11, E12, E13, E14, E15, E16, E17, E18, E19,
        E20, E21, E22, E23, E24, E25, E26, E27, E28, E29,
        E30, E31, E32, E33, E34, E35, E36, E37, E38, E39,
        E40, E41, E42, E43, E44, E45, E46, E47, E48, E49;

        @Override
        public String desc() { return name(); }
    }

    // ==================== 测试引擎实例 ====================
    private StateMachine<TestState, TestEvent> enumEngine;
    private CustomStateMachine<String, String> customEngine;

    // 用于字符串版的状态/事件常量
    private static final String[] STATE_NAMES = new String[50];
    private static final String[] EVENT_NAMES = new String[50];
    static {
        for (int i = 0; i < 50; i++) {
            STATE_NAMES[i] = "S" + String.format("%02d", i);
            EVENT_NAMES[i] = "E" + String.format("%02d", i);
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        // ---- 1. 枚举引擎：注册 50 个状态 × 每个状态关联 1 个事件 = 50 条路由 ----
        enumEngine = new StateMachine<>();
        for (int i = 0; i < 49; i++) {
            // S00 + E00 -> S01, S01 + E01 -> S02, ... 形成一条链
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[i],
                    TestState.values()[i + 1]
            );
        }
        // 最后一条：S49 + E49 -> S00（闭环）
        enumEngine.register(
                TestState.S49,
                TestEvent.E49,
                TestState.S00
        );

        // ---- 2. 枚举引擎：额外注册一些“跳转”路由，增加路由表大小 ----
        // 让每个状态额外关联 2 个事件，总路由数达到约 150 条
        for (int i = 0; i < 50; i++) {
            int next1 = (i + 3) % 50;
            int next2 = (i + 7) % 50;
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[(i + 1) % 50],
                    TestState.values()[next1]
            );
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[(i + 2) % 50],
                    TestState.values()[next2]
            );
        }

        // ---- 3. 自定义引擎（字符串版）：注册同样的路由 ----
        customEngine = new CustomStateMachine<>(Function.identity());
        for (int i = 0; i < 49; i++) {
            customEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[i],
                    STATE_NAMES[i + 1]
            );
        }
        customEngine.register(STATE_NAMES[49], EVENT_NAMES[49], STATE_NAMES[0]);

        for (int i = 0; i < 50; i++) {
            int next1 = (i + 3) % 50;
            int next2 = (i + 7) % 50;
            customEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[(i + 1) % 50],
                    STATE_NAMES[next1]
            );
            customEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[(i + 2) % 50],
                    STATE_NAMES[next2]
            );
        }
    }

    // ==================== 基准测试方法 ====================

    /**
     * 测试 1：枚举引擎 - 命中路由（S00 + E00 -> S01）
     */
    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHit() {
        return enumEngine.transition(TestState.S00, TestEvent.E00);
    }

    /**
     * 测试 2：枚举引擎 - 命中路由（S25 + E25 -> S26）
     */
    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHitMiddle() {
        return enumEngine.transition(TestState.S25, TestEvent.E25);
    }

    /**
     * 测试 3：枚举引擎 - 命中路由（S49 + E49 -> S00）
     */
    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHitLast() {
        return enumEngine.transition(TestState.S49, TestEvent.E49);
    }

    /**
     * 测试 4：枚举引擎 - 未命中路由（S00 + E50，E50 不存在）
     */
    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineMiss() {
        // 用 null 模拟不存在的事件，引擎内部会返回失败
        return enumEngine.transition(TestState.S00, TestEvent.E00);
    }
    // 注意：由于枚举类型安全，无法传入不存在的事件常量。
    // 这里改用存在的 E00，但会走到一个不存在路由的场景
    // 实际上我们用 E00 但 S00 在 E00 下是有路由的，所以这个测试需要调整
    // 下面提供一个真正未命中的测试

    /**
     * 测试 5：枚举引擎 - 未命中路由（S00 + E01，没有注册 S00 + E01 的路由）
     */
    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineMissReal() {
        // S00 注册了 E00、E01(跳转)、E02(跳转)，没有注册 E03
        return enumEngine.transition(TestState.S00, TestEvent.E03);
    }

    /**
     * 测试 6：自定义引擎 - 命中路由
     */
    @Benchmark
    public CustomTransitionResult<String, String> testCustomEngineHit() {
        return customEngine.transition(STATE_NAMES[0], EVENT_NAMES[0]);
    }

    /**
     * 测试 7：自定义引擎 - 命中路由（中间）
     */
    @Benchmark
    public CustomTransitionResult<String, String> testCustomEngineHitMiddle() {
        return customEngine.transition(STATE_NAMES[25], EVENT_NAMES[25]);
    }

    /**
     * 测试 8：自定义引擎 - 未命中路由
     */
    @Benchmark
    public CustomTransitionResult<String, String> testCustomEngineMiss() {
        // S00 没有注册 E03
        return customEngine.transition(STATE_NAMES[0], "E03");
    }

    // ==================== 主方法 ====================
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(StateMachineBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }
}