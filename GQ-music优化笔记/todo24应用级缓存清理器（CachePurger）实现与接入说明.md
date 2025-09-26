## todo24应用级缓存清理器（CachePurger）实现与接入说明

一、为什么需要它
- 我们同时使用了两种缓存途径：
  - Spring Cache 注解（如 `@Cacheable/@CacheEvict`）管理的命名空间（例如 `songCache/albumCache/...`）
  - 业务侧直接通过 RedisTemplate 写入的 Key（例如推荐列表 `recommended_songs:*`）
- `@CacheEvict` 只能清理 Spring Cache 管理的缓存，无法删除你手写前缀 Key。为保证“删除数据后缓存不脏”，引入应用级清理器统一删除这些业务 Key。

二、核心实现
文件：`cn/edu/seig/vibemusic/util/CachePurger.java`

职责：
- 提供按“业务事件（删歌手/专辑/歌曲/歌单）”清理一组 Redis 前缀的能力
- 目前默认清理前缀：`recommended_songs:*`

关键代码
```1:48:src/main/java/cn/edu/seig/vibemusic/util/CachePurger.java
package cn.edu.seig.vibemusic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CachePurger {

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CachePurger(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 通用：按前缀批量删除
    private void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // 删除与用户推荐/榜单等有关的列表缓存
    private void purgeRecommendationLists() {
        deleteByPrefix("recommended_songs:");
    }

    public void purgeForArtist(Long artistId) {
        purgeRecommendationLists();
    }

    public void purgeForAlbum(Long albumId) {
        purgeRecommendationLists();
    }

    public void purgeForSong(Long songId) {
        purgeRecommendationLists();
    }

    public void purgeForPlaylist(Long playlistId) {
        purgeRecommendationLists();
    }
}
```

三、接入位置
- 删除歌手：`ArtistServiceImpl.cascadeDeleteArtist(...)` 末尾调用
```420:428:src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java
// 4) 删除歌手头像与歌手记录
...
// 5) 兜底清理
cleanupOrphans();
// 6) 额外：清理自定义 Redis Key（如推荐列表）
try { cachePurger.purgeForArtist(artistId); } catch (Exception ignored) {}
```

- 删除专辑：`AlbumServiceImpl.deleteAlbum(...)` 末尾调用
```247:250:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
// 6) 自定义 Redis Key 清理
try { cachePurger.purgeForAlbum(albumId); } catch (Exception ignored) {}
```

- 删除歌曲：`SongServiceImpl.deleteSong/deleteSongs` 成功后调用
```682:683:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
try { cachePurger.purgeForSong(songId); } catch (Exception ignored) {}
```
```732:733:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
try { cachePurger.purgeForSong(null); } catch (Exception ignored) {}
```

- 删除歌单：`PlaylistServiceImpl.deletePlaylist/deletePlaylists` 成功后调用
```413:416:src/main/java/cn/edu/seig/vibemusic/service/impl/PlaylistServiceImpl.java
try { cachePurger.purgeForPlaylist(playlistId); } catch (Exception ignored) {}
```
```438:444:src/main/java/cn/edu/seig/vibemusic/service/impl/PlaylistServiceImpl.java
try { cachePurger.purgeForPlaylist(null); } catch (Exception ignored) {}
```

四、扩展前缀清单
- 如后续新增自定义缓存（例：`home:rank:*`、`artist:detail:*`），在 `CachePurger` 内新增：
  - 对应 `deleteByPrefix("home:rank:")` 等
  - 并在相应 `purgeForXxx` 方法里调用
- 如果某些前缀与“用户”或“实体 ID”强相关，建议带 ID 前缀设计如 `artist:detail:{id}`，便于精确删除；当前我们对推荐列表采用“全量前缀删除”，简单但粗粒度。

五、与 Spring Cache 的配合
- 保持原有 `@CacheEvict(allEntries=true)` 用于清理 Spring Cache 命名空间（`songCache/albumCache/...`）
- `CachePurger` 补齐“非 Spring Cache、自定义 Redis Key”的清理
- 两者合并才能保证“删除后缓存不脏”

六、错误处理与并发
- 清理器调用包装在 try/catch 中，避免删除链路因缓存清理异常而中断
- Redis keys() 在大规模生产环境可能有阻塞风险，如前缀下键数巨大建议换用：
  - 事先维护“索引集合”，用 `SMEMBERS` + `DEL` 批量删
  - 或使用 SCAN 游标分批删除（需要封装）

七、测试要点
- 删除歌手/专辑/歌曲/歌单后：
  - Redis 中 `recommended_songs:*` 被清空（或 SCAN 无返回）
  - 前端重新拉取数据，不再出现已删除的歌曲/专辑/歌单/歌手
- 反复执行删除，确保幂等；异常不影响主业务返回

