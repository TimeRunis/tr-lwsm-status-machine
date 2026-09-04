# TR-LWSM 轻量级状态机引擎

一个零依赖、轻量、类型安全的状态机核心库，支持枚举、字符串、POJO 等任意状态/事件类型，适用于订单流转、审批流程、支付回调等业务场景。

## 核心特性

- **零依赖**：纯 Java 实现，不引入任何第三方库
- **无状态线程安全**：引擎不持有业务状态，路由表使用并发容器，可安全用于高并发场景
- **统一泛型引擎**：`LwsmStateMachine<S, E, C>` 一套引擎同时支持枚举、字符串、POJO
- **优雅 DSL**：`from -> on -> to -> guard -> action` 链式注册路由
- **guard/action**：使用 JDK 标准 `Predicate` / `Consumer`，配合公共上下文基类 `LwsmContent<E, C>` 传递参数
- **高性能**：经 JMH 基准测试，50 状态 / 150+ 路由下吞吐量稳定在 2400 万 QPS 以上

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
    .guard(content -> content.content().getOrder().getAmount() > 0)
    .action(content -> content.content().getPaymentService().create(content.content().getOrder()));
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

## 公共上下文基类

`LwsmContent<E, C>` 是所有 guard/action 的统一入参：

```java
public class LwsmContent<E, C> {
    E event();      // 当前事件
    C content();    // 业务上下文
}
```

- guard：`Predicate<LwsmContent<E, C>>`
- action：`Consumer<LwsmContent<E, C>>`

## API 说明

| 方法 | 返回值 | 说明 |
|:---|:---|:---|
| `from(source)` | `FromBuilder` | DSL 起点 |
| `on(event)` | `OnBuilder` | 指定事件 |
| `to(target)` | `ToBuilder` | 指定目标状态 |
| `guard(Predicate<LwsmContent<E,C>>)` | `ToBuilder` | 守卫条件 |
| `action(Consumer<LwsmContent<E,C>>)` | `LwsmStateMachine` | 注册并绑定动作 |
| `register()` | `LwsmStateMachine` | 无 action 时注册路由 |
| `transition(current, event[, context])` | `TransitionResult<S,E>` | 纯状态计算，不执行 action |
| `fire(current, event[, context])` | `TransitionResult<S,E>` | 计算并执行 action |
| `transitionWillThrow(current, event[, context])` | `S` | 失败抛 `IllegalStateException` |

## 性能测试

使用 JMH 基准测试，50 个状态、150+ 条路由下的吞吐量表现：

| 测试场景 | 吞吐量 (ops/us) | 约 QPS |
| :--- | :--- | :--- |
| 字符串引擎命中 | 28.29 | 2829 万/秒 |
| 字符串引擎未命中 | 25.17 | 2517 万/秒 |
| 枚举引擎命中 | 24.42 | 2442 万/秒 |
| 枚举引擎未命中 | 23.41 | 2341 万/秒 |

> 结论：单次状态计算约 35-40 纳秒，相对于一次数据库查询（毫秒级）可忽略不计。状态数量对性能无影响。

## 设计原则

- **单一职责**：引擎只负责路由计算、守卫判断、动作触发
- **开闭原则**：通过泛型和 DSL 扩展，无需修改引擎核心代码
- **解耦**：业务逻辑通过 guard/action 注入，引擎不感知具体业务

## 许可证

MIT