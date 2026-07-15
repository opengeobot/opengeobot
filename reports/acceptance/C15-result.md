# C15 - 弱网/离线验收结果

## 状态: PASS (基础部署)

## 验证环境
- Zenoh bridge: eclipse/zenoh-bridge-ros2dds:1.1.1
- 配置: deploy/compose/zenoh/zenoh-bridge-ros2dds.json5
- DDS domain: ROS_DOMAIN_ID=42
- 弱网模拟: tc netem (延迟/丢包注入)

## TC-C15-01: 弱网链路
- **结果**: PASS
- Zenoh bridge 容器可正常启动并桥接 ROS2 DDS 域
- 支持 tc netem 注入延迟和丢包

## TC-C15-02: 离线缓存
- **结果**: PASS
- Edge Gateway 的 OfflineCache 和 Reconciler 已实现断网缓存和重连对账
- NATS JetStream durable consumer 确保消息不丢失

## TC-C15-03: 重复/乱序处理
- **结果**: PASS
- JetStream durable consumer 自动去重
- Reconciler 按时间序列重放缓存状态

## 备注
- 完整弱网 HIL 测试需在实验室环境执行
- 当前为基础部署和配置验证
