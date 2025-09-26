### 缓存相关运维手册（GQ Music）

一、日常检查清单
- Redis 连接/状态
  - 确认 Redis 可达：ping，慢查询、内存、水位告警
  - 连接数/阻塞：`INFO clients`，关注 blocked_clients、connected_clients
- 命名空间核验（Spring Cache 与自定义 Key）
  - Spring Cache：按命名空间抽样查看 keys 数量（建议 SCAN）
  - 自定义 Key：`recommended_songs:*`、`verificationCode:*`、`HOT_SEARCH_ZSET` 等

二、常用命令（生产建议用 SCAN 替代 KEYS）
- 统计命名空间 Key 数
  - `SCAN 0 MATCH songCache:* COUNT 1000`（分页游标）
  - 将游标遍历直到返回 0；也可在脚本中计数
- 删除某前缀 Key（低峰期执行）
  - `SCAN 0 MATCH recommended_songs:* COUNT 1000` → 收集结果 → `DEL k1 k2 ...`
  - 不建议直接 `KEYS prefix*` 在线上执行
- 查看一个 Key
  - String/List/Set/Hash 对应 `GET/LRANGE/SMEMBERS/HGETALL`
  - TTL：`TTL key`
- 熔断性清空（谨慎）
  - 全库：`FLUSHALL`（强烈不推荐在线上）
  - 指定 DB：`FLUSHDB`（不推荐，除非确认无共享数据）

三、命中率与延迟观测
- 采集维度
  - 命中率：命中次数/请求次数（可在服务内暴露指标，或通过 access log 与埋点上报）
  - 访问延迟：Redis RT（客户端 metrics），接口 RT（网关/服务）
  - 失败计数：超时、连接拒绝、序列化失败
- 简易基线
  - Spring Cache 命中率（专辑/歌曲列表）：>70% 为佳（受业务访问分布影响）
  - 自定义推荐列表命中率：>60%（30 分钟 TTL）为佳
  - Redis P99 < 5ms；如 >10ms 需关注网络与慢查询

四、故障排查思路
- 删除后仍返回旧数据
  - 是否来自自定义 Key？检查 `recommended_songs:*` 是否未清理
  - 是否为浏览器/前端 store 缓存？清掉本地 store 重试
  - 是否存在多实例/多 CacheManager？确认统一 Redis 与序列化配置
- @CacheEvict 不生效
  - 方法是否被 AOP 代理（同类内部调用无法触发 Spring AOP → 可抽服务/接口调用） 
  - cacheNames 是否一致，key 生成规则是否匹配
  - 是否用了 `unless/condition` 导致不缓存或不清理
- Redis 侧抖动/超时
  - keys/大 key 导致阻塞：确认是否有 KEYS 在高峰使用
  - 大 value 序列化慢：检查 value 体积与 Jackson 序列化配置
  - 连接上限：`maxclients`、连接泄露（客户端未关闭）

五、预防缓存穿透/击穿/雪崩
- 穿透（查不存在的 Key）：
  - 对不存在的数据写入空值缓存，设置较短 TTL（如 60s）
  - 接口参数前置校验（ID 非法不打到 Redis/DB）
- 击穿（单 Key 失效瞬间高并发打 DB）：
  - 热 Key 提前续期或加随机抖动 TTL（目前推荐列表 30 分钟，可加 ±5 分钟抖动）
  - 加互斥锁 rebuild（可选：Redisson）
- 雪崩（大量 Key 同时过期）：
  - TTL 分布引入随机散列，避免集中
  - 核心命名空间以 `@CacheEvict` 为主（写操作清空），弱化 TTL 依赖

六、容量与大 Key 管理
- 容量水位
  - 定期查看 `INFO memory`，关注 used_memory、used_memory_peak
  - 设置 `maxmemory` 与淘汰策略（volatile-lru/volatile-ttl/allkeys-lru 等按需）
- 大 Key 识别
  - 使用 `MEMORY USAGE key` 抽样
  - 或在低峰 SCAN 全库、对 value 体积超阈的 key 记录
- 优化策略
  - 减少大对象缓存（分页数据可拆分），或缩短 TTL
  - 统一 JSON 序列化方式，避免重复字段膨胀

七、灰度与回滚建议
- 引入新前缀或大改 TTL 时先在灰度实例生效，观察 24h
- 变更 `CachePurger` 前缀清理范围时，先在预生产验证 Delete 行为无误

八、与代码配合（本项目）
- 删除链路调用顺序
  - 先删 MinIO/DB → 再 `@CacheEvict` → 最后 `CachePurger` 清自定义 Key
- 需要新增前缀时
  - 在 `CachePurger` 中追加 `deleteByPrefix("your_prefix:")`
  - 在相应删除/更新服务方法中调用对应 `purgeForXxx`
- 建议增加手动清理接口（只限内部使用）
  - `POST /admin/maintenance/cleanup` → 调用 `CachePurger` 某些方法，便于一键清理

九、运维脚本（示例）
- SCAN 删除指定前缀（伪代码，放在跳板机执行）
  - Python/Go/Node 可用 redis 客户端实现：
    - 遍历 `SCAN cursor MATCH prefix:* COUNT 1000`
    - 批量 DEL 收集到的 keys（小批量：每批 500～1000 个）

十、应急手册（快速操作）
- 删除某专辑相关缓存
  - Spring Cache：触发对应 API 的 @CacheEvict（如更新专辑名）
  - 自定义 Key：调用内部维护接口或在 Redis 上 `SCAN+DEL recommended_songs:*`
- 全量推荐列表失真
  - `CachePurger` 临时扩展：增加 `purgeAllRecommendations()`，一次清空 `recommended_songs:*`

若你提供 Redis 版本与部署方式（单机/主从/Sentinel/Cluster），我可以给更精确的监控项与告警阈值（Prometheus/Grafana 面板模板、指标名与告警规则）。