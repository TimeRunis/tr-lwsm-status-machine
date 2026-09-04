# TR-LWSM 轻量级状态机引擎

一个零依赖、轻量、类型安全的状态机核心库，支持枚举、字符串、POJO 等任意状态/事件类型，适用于订单流转、审批流程、支付回调等业务场景。

## 核心特性

- **零依赖**：纯 Java 实现，不引入任何第三方库
- **无状态线程安全**：引擎不持有业务状态，路由表使用并发容器，可安全用于高并发场景
- **统一泛型引擎**：`LwsmStateMachine<S, E, C>` 一套引擎同时支持枚举、字符串、POJO
- **优雅 DSL**：`from -> on -> to -> guard -> action` 链式注册路由
- **Bi 类型 guard/action**：使用 JDK 标准 `BiPredicate<C, E>` / `BiConsumer<C, E>`，直接传入业务上下文和事件
- **多 transition**：同一个 `(source, event)` 支持注册多条路由，按注册顺序匹配，第一个 guard 通过的路由生效
- **高性能**：经 JMH 基准测试，50 状态 / 150+ 路由下吞吐量稳定在 2000 万 QPS 以上

## 快速开始

### 1. 定义状态、事件和业务上下文

```java
enum OrderState {
    INIT, PAYING, PAID, CANCEL;
}

enum OrderEvent {
    PAY, CANCEL, SUCCESS;
}

// 业务上下文：可以放订单、服务等
class OrderContext {
    private final Order order;
    private final PaymentService paymentService;
    // constructor/getter...
}
```

### 2. 创建引擎并注册路由

```java
LwsmStateMachine<OrderState, OrderEvent, OrderContext> engine =
        new LwsmStateMachine<>();

engine
    .from(OrderState.INIT)
    .on(OrderEvent.PAY)
    .to(OrderState.PAYING)
    .guard((context, event) -> context.getOrder().getAmount() > 0)
    .action((context, event) -> context.getPaymentService().create(context.getOrder()));
```

### 3. 执行流转

```java
// 纯计算，不执行 action
TransitionResult<OrderState, OrderEvent> result =
        engine.transition(OrderState.INIT, OrderEvent.PAY, context);

// 计算并执行 action
engine.fire(OrderState.INIT, OrderEvent.PAY, context);

// 失败直接抛异常
OrderState target = engine.transitionWillThrow(OrderState.INIT, OrderEvent.PAY, context);
```

## 多 transition 示例

同一个 `(INIT, PAY)` 可以注册多条路由，按注册顺序匹配：

```java
engine
    .from(OrderState.INIT)
    .on(OrderEvent.PAY)
    .to(OrderState.PAYING)
    .guard((context, event) -> context.getAmount() > 1000)
    .register();

engine
    .from(OrderState.INIT)
    .on(OrderEvent.PAY)
    .to(OrderState.PAID)
    .guard((context, event) -> context.getAmount() <= 1000)
    .register();
```

执行时：

- 第一条 guard 通过，走 `PAYING`；
- 第一条不通过，继续尝试下一条；
- 全部 guard 都不通过，返回失败。

如果某条 transition 没有 guard，它等价于“默认路由”，会在前面 guard 都不通过时命中。

## 字符串 / POJO 场景

同一个引擎同样支持字符串或任意 POJO：

```java
LwsmStateMachine<String, String, Void> engine = new LwsmStateMachine<>();

engine
    .from("INIT")
    .on("PAY")
    .to("PAYING")
    .register();

String target = engine.transition("INIT", "PAY").getTarget();
// target = "PAYING"
```

## guard / action 参数

- guard：`BiPredicate<C, E>`
  - 第一个参数是业务上下文 `C`
  - 第二个参数是事件 `E`
  - 返回 `true` 表示允许这条 transition

- action：`BiConsumer<C, E>`
  - 第一个参数是业务上下文 `C`
  - 第二个参数是事件 `E`
  - 在该 transition 被选中且使用 `fire(...)` 时执行

## 失败原因与 Action 异常

失败时会明确区分原因，可以通过 `TransitionResult#getFailReason()` 判断：

| FailReason | 含义 |
|:---|:---|
| `NO_ROUTE` | 当前状态/事件没有可用的路由 |
| `GUARD_REJECTED` | 路由存在，但所有 guard 均未通过 |

Action 异常不会捕获或吞掉：使用 `fire(...)` 时，如果 action 抛出异常，会直接向上传播给调用方。

```java
TransitionResult<S, E> result = engine.transition(current, event, context);
if (!result.isSuccess()) {
    if (result.getFailReason() == TransitionResult.FailReason.NO_ROUTE) {
        // 处理无路由
    } else if (result.getFailReason() == TransitionResult.FailReason.GUARD_REJECTED) {
        // 处理守卫拒绝
    }
}
```

## API 说明

| 方法 | 返回值 | 说明 |
|:---|:---|:---|
| `from(source)` | `FromBuilder` | DSL 起点 |
| `on(event)` | `OnBuilder` | 指定事件 |
| `to(target)` | `ToBuilder` | 指定目标状态 |
| `guard(BiPredicate<C,E>)` | `ToBuilder` | 守卫条件 |
| `action(BiConsumer<C,E>)` | `LwsmStateMachine` | 注册并绑定动作 |
| `register()` | `LwsmStateMachine` | 无 action 时注册路由 |
| `transition(current, event[, context])` | `TransitionResult<S,E>` | 纯状态计算，不执行 action |
| `fire(current, event[, context])` | `TransitionResult<S,E>` | 计算并执行 action |
| `transitionWillThrow(current, event[, context])` | `S` | 失败抛 `IllegalStateException` |

## 性能测试

使用 JMH 基准测试，50 个状态、150+ 条路由下的吞吐量表现：

| 测试场景 | 吞吐量 (ops/us) | 约 QPS |
| :--- | :--- | :--- |
| 枚举引擎命中 | ~62 | 约 6200 万/秒 |
| 枚举引擎未命中 | ~21 | 约 2100 万/秒 |
| 字符串引擎命中 | ~62 | 约 6200 万/秒 |
| 字符串引擎未命中 | ~22 | 约 2200 万/秒 |

> 结论：单次状态计算性能非常高，相对于一次数据库查询可忽略不计。状态数量对性能无影响。

## 设计原则

- **单一职责**：引擎只负责路由计算、守卫判断、动作触发
- **开闭原则**：通过泛型和 DSL 扩展，无需修改引擎核心代码
- **解耦**：业务逻辑通过 guard/action 注入，引擎不感知具体业务

## 许可证

MIT