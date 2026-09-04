package com.tr.lwsm;

import com.tr.lwsm.obj.LwsmStateMachine;
import com.tr.lwsm.obj.entity.TransitionResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

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

    // ==================== 50 个状态的枚举 ====================
    enum TestState {
        S00, S01, S02, S03, S04, S05, S06, S07, S08, S09,
        S10, S11, S12, S13, S14, S15, S16, S17, S18, S19,
        S20, S21, S22, S23, S24, S25, S26, S27, S28, S29,
        S30, S31, S32, S33, S34, S35, S36, S37, S38, S39,
        S40, S41, S42, S43, S44, S45, S46, S47, S48, S49;
    }

    enum TestEvent {
        E00, E01, E02, E03, E04, E05, E06, E07, E08, E09,
        E10, E11, E12, E13, E14, E15, E16, E17, E18, E19,
        E20, E21, E22, E23, E24, E25, E26, E27, E28, E29,
        E30, E31, E32, E33, E34, E35, E36, E37, E38, E39,
        E40, E41, E42, E43, E44, E45, E46, E47, E48, E49;
    }

    // ==================== 测试引擎实例 ====================
    private LwsmStateMachine<TestState, TestEvent, Void> enumEngine;
    private LwsmStateMachine<String, String, Void> stringEngine;

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
        // ---- 1. 枚举引擎 ----
        enumEngine = new LwsmStateMachine<>();
        for (int i = 0; i < 49; i++) {
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[i],
                    TestState.values()[i + 1]);
        }
        enumEngine.register(TestState.S49, TestEvent.E49, TestState.S00);

        for (int i = 0; i < 50; i++) {
            int next1 = (i + 3) % 50;
            int next2 = (i + 7) % 50;
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[(i + 1) % 50],
                    TestState.values()[next1]);
            enumEngine.register(
                    TestState.values()[i],
                    TestEvent.values()[(i + 2) % 50],
                    TestState.values()[next2]);
        }

        // ---- 2. 字符串引擎 ----
        stringEngine = new LwsmStateMachine<>();
        for (int i = 0; i < 49; i++) {
            stringEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[i],
                    STATE_NAMES[i + 1]);
        }
        stringEngine.register(STATE_NAMES[49], EVENT_NAMES[49], STATE_NAMES[0]);

        for (int i = 0; i < 50; i++) {
            int next1 = (i + 3) % 50;
            int next2 = (i + 7) % 50;
            stringEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[(i + 1) % 50],
                    STATE_NAMES[next1]);
            stringEngine.register(
                    STATE_NAMES[i],
                    EVENT_NAMES[(i + 2) % 50],
                    STATE_NAMES[next2]);
        }
    }

    // ==================== 基准测试方法 ====================

    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHit() {
        return enumEngine.transition(TestState.S00, TestEvent.E00);
    }

    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHitMiddle() {
        return enumEngine.transition(TestState.S25, TestEvent.E25);
    }

    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineHitLast() {
        return enumEngine.transition(TestState.S49, TestEvent.E49);
    }

    @Benchmark
    public TransitionResult<TestState, TestEvent> testEnumEngineMiss() {
        return enumEngine.transition(TestState.S00, TestEvent.E03);
    }

    @Benchmark
    public TransitionResult<String, String> testStringEngineHit() {
        return stringEngine.transition(STATE_NAMES[0], EVENT_NAMES[0]);
    }

    @Benchmark
    public TransitionResult<String, String> testStringEngineHitMiddle() {
        return stringEngine.transition(STATE_NAMES[25], EVENT_NAMES[25]);
    }

    @Benchmark
    public TransitionResult<String, String> testStringEngineMiss() {
        return stringEngine.transition(STATE_NAMES[0], "E03");
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(StateMachineBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }
}