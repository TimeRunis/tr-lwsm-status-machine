# TR-LWSM 轻量级状态机引擎

一个零依赖、无状态、类型安全的状态机核心库，支持枚举和自定义状态类型，适用于订单流转、审批流程、支付回调等业务场景。

## 核心特性

- **零依赖**：纯 Java 实现，不引入任何第三方库
- **无状态线程安全**：引擎不持有业务状态，可安全用于高并发场景
- **灵活**：
    - `StateMachine`：状态/事件需实现接口，适合枚举用户，类型安全
    - `CustomStateMachine`：无类型约束，适合字符串或 POJO 场景，灵活自由
- **轻量级 API**：一行代码完成状态计算
- **高性能**：经 JMH 基准测试，50 状态 / 150+ 路由下吞吐量稳定在 2400 万 QPS 以上

## 快速开始

### 1. 获取引擎包

**本地构建**
```bash
git clone https://github.com/TimeRunis/tr-lwsm-status-machine.git
cd tr-lwsm-status-machine
mvn clean install
```

### 方式一：枚举版

```java
// 1. 定义状态和事件枚举
enum OrderState implements LwsmState {
    INIT, PAYING, PAID, CANCEL;
    @Override public String desc() { return name(); }
}

enum OrderEvent implements LwsmEvent {
    PAY, CANCEL, SUCCESS;
    @Override public String desc() { return name(); }
}

// 2. 创建引擎并注册路由
StateMachine<OrderState, OrderEvent> engine = new StateMachine<>();
engine.register(OrderState.INIT, OrderEvent.PAY, OrderState.PAYING)
      .register(OrderState.PAYING, OrderEvent.SUCCESS, OrderState.PAID)
      .register(OrderState.INIT, OrderEvent.CANCEL, OrderState.CANCEL);

// 3. 执行业务（一行代码完成状态计算）
OrderState target = engine.transition(OrderState.INIT, OrderEvent.PAY);
// target = PAYING
```

### 方式二：自定义版

```java
CustomStateMachine<String, String> engine = new CustomStateMachine<>(Function.identity());
engine.register("INIT", "PAY", "PAYING")
      .register("PAYING", "SUCCESS", "PAID");

String target = engine.fire("INIT", "PAY");
// target = "PAYING"
```

### 方式三：自定义版（POJO 场景）

```java
// 状态和事件可以是任意对象
class MyState {
    private String code;
    // 确保 toString() 返回唯一标识
    @Override public String toString() { return code; }
}

// 提供解析器，告诉引擎如何从字符串还原对象
Map<String, MyState> stateCache = ...;
CustomStateMachine<MyState, MyEvent> engine = 
    new CustomStateMachine<>(stateCache::get);
```

## API 说明

| 方法                                    | 返回值                                                   | 说明 |
|:--------------------------------------|:------------------------------------------------------| :--- |
| `transition(current, event)`          | `TransitionResult<S,E> 或 CustomTransitionResult<S,E>` | 执行流转，返回结果对象（含成功/失败、目标状态、错误信息） |
| `transitionWillThrow(current, event)` | `S`（目标状态）                                             | 执行流转，失败直接抛出 `IllegalStateException` |


```java
// 推荐用法：业务层直接用 transitionWillThrow，简洁明了
OrderState target = engine.transitionWillThrow(current, event);

// 需要细粒度控制时用 transition
TransitionResult<OrderState, OrderEvent> result = engine.transition(current, event);
if (result.isSuccess()) {
    // 正常处理
} else {
    // 降级处理
}
```

## 性能测试

使用 JMH 基准测试，50 个状态、150+ 条路由下的吞吐量表现：

| 测试场景 | 吞吐量 (ops/us) | 约 QPS |
| :--- | :--- | :--- |
| Custom 引擎命中 | 28.29 | 2829 万/秒 |
| Custom 引擎未命中 | 25.17 | 2517 万/秒 |
| Enum 引擎命中 | 24.42 | 2442 万/秒 |
| Enum 引擎未命中 | 23.41 | 2341 万/秒 |

> 结论：单次状态计算约 35-40 纳秒，相对于一次数据库查询（毫秒级）可忽略不计。状态数量对性能无影响。

## 设计原则

- **单一职责**：引擎只负责状态路由计算
- **开闭原则**：通过 `State`/`Event` 接口扩展，无需修改引擎核心代码
- **约定优于配置**：默认支持枚举类型，开箱即用

## 许可证

MIT
