### 本项目缓存机制总览（Spring Cache + RedisTemplate）

一、总体设计
- 双轨缓存方案：
  - Spring Cache 注解缓存：命名空间化、透明读写，使用 `@Cacheable/@CacheEvict` 管理业务热点数据
  - 业务自定义缓存：通过 `RedisTemplate` 直接读写特定 Key（如推荐列表、验证码、热搜等）
- 统一清理保障：
  - 删除/更新链路使用 `@CacheEvict(allEntries=true)` 清理命名空间
  - 应用级清理器 `CachePurger` 清理非 Spring Cache 的自定义 Key（例如 `recommended_songs:*`）

二、Spring Cache 使用约定
- 启用位置：`VibeMusicServerApplication` 上 `@EnableCaching`
- 主要命名空间（cacheNames）：
  - `songCache`：歌曲相关列表/详情
  - `albumCache`：专辑相关
  - `artistCache`：歌手相关
  - `playlistCache`：歌单相关
  - 其它：`userCache/userFavoriteCache/bannerCache/feedbackCache/lyricCache` 等
- 典型用法
  - 读：`@Cacheable(cacheNames="albumCache", key="...")`（本项目多使用 `@Cacheable(key=...)` 或按方法参数拼 key）
  - 写/删：`@CacheEvict(cacheNames="albumCache", allEntries=true)`（更新/删除后整体清理，避免局部 key 漏删）
- 关键示例
  - 专辑分页/详情：`AlbumServiceImpl.getAlbumsByArtist / getAlbumDetail`
  - 歌曲分页/详情：`SongServiceImpl.getAllSongs / getSongDetail`
  - 歌手相关：`ArtistServiceImpl.getAllArtists* / getArtistDetail`
  - 歌单相关：`PlaylistServiceImpl` 系列方法
- 更新/删除链路中的清理
  - 歌曲：`@CacheEvict(cacheNames={"songCache","artistCache"}, allEntries=true)`
  - 专辑：`@CacheEvict(cacheNames={"albumCache","songCache"}, allEntries=true)`
  - 歌手：`@CacheEvict(cacheNames="artistCache", allEntries=true)`
  - 歌单：`@CacheEvict(cacheNames="playlistCache", allEntries=true)`
  - 评论/收藏等跨对象操作：相应服务中也会清理受影响的 cacheNames

三、自定义 RedisTemplate 缓存
- 推荐列表（按用户风格推荐）：
  - 写入：`SongServiceImpl` 使用 `RedisTemplate<String, SongVO>` 写入 `recommended_songs:{userId}` 列表，并设置 TTL 30 分钟
  - 读取：先 `range`，无则回源 DB 并回写
- 验证码、热搜：
  - `StringRedisTemplate` 写入 Key（如 `verificationCode:{email}`、`HOT_SEARCH_ZSET`）
- 特点：
  - Key 不是由 Spring Cache 管理，`@CacheEvict` 不会自动清理
  - 需要在删除/更新链路中显式清理（已用 `CachePurger` 解决）

四、应用级清理器（CachePurger）
- 目的：清理非 Spring Cache 的自定义 Key
- 现清理前缀：`recommended_songs:*`
- 接入点：
  - 删除歌手：`ArtistServiceImpl.cascadeDeleteArtist` 末尾 `purgeForArtist`
  - 删除专辑：`AlbumServiceImpl.deleteAlbum` 末尾 `purgeForAlbum`
  - 删除歌曲：`SongServiceImpl.deleteSong/deleteSongs` 成功后 `purgeForSong`
  - 删除歌单：`PlaylistServiceImpl.deletePlaylist/deletePlaylists` 成功后 `purgeForPlaylist`
- 可扩展：如有 `home:rank:*` 等前缀，直接在 `CachePurger` 内追加 `deleteByPrefix` 调用

五、Key 设计与失效策略
- Spring Cache
  - Key 生成：默认基于方法参数与 SpEL；本项目多采用 `allEntries=true` 的命名空间整体清理，降低漏删风险
  - 失效时间：由 CacheManager/Redis 设置（如需全局 TTL 可在 `RedisCacheConfiguration` 配置；当前项目未显式声明全局 TTL，更多依赖“更新即清空”策略）
- 自定义 Key
  - 推荐列表：`recommended_songs:{userId}`，TTL 30 分钟（热点高且易变）
  - 验证码：TTL 5 分钟
  - 其它 Key（热搜）由业务决定

六、常见问题与处理
- “删除后仍读到旧缓存”
  - 原因：数据来自自定义 Key 而非 Spring Cache；`@CacheEvict` 无法清理；或前端/内存 Store 存留
  - 处理：在删除链路接入 `CachePurger`；必要时同时清理前端 store 或加强接口 side effect
- “keys + delete” 在生产高 QPS 下的注意
  - `KEYS prefix*` 对 Redis 是阻塞操作，Key 数量非常大时可能卡顿
  - 可替换为：
    - 建立“索引集合”记录实际 Key，删除时按集合成员删除
    - 使用 `SCAN` 游标分批删除
  - 目前项目数据规模可接受；如访问量继续上升，我们可升级为 SCAN 实现

七、实践建议
- 所有“写路径”（新增/更新/删除）务必带上与之关联的 `@CacheEvict`，并在需要时调用 `CachePurger`
- 尽量将自定义 Key 设计为“可推导的前缀 + 主体 ID”，方便准确删除
- 如果引入“细粒度局部 Evict”，需保证 Key 生成规则一致，否则容易漏删；当前我们采用命名空间全清的策略，简单且稳妥

