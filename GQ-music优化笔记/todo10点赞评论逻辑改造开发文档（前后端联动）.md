## todo10点赞评论逻辑改造开发文档（前后端联动）

#### 背景与问题
- 现象：评论可被同一用户无限点赞；并发下可能出现计数不准或负数。
- 原因：
  - 前端始终调用“点赞”接口，没有取消点赞分支与本地状态控制。
  - 后端使用“查→改→存”更新 `like_count`，并发覆盖风险，且未防负数。

#### 改造目标
- 点赞/取消点赞可切换（同一会话内防重复触发）。
- 后端以“原子自增/自减”更新 `tb_comment.like_count`，并发安全，最小为 0。

---

### 后端改造

#### 1) Mapper 增加原子自增/自减 SQL
```15:25:src/main/java/cn/edu/seig/vibemusic/mapper/CommentMapper.java
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /** 点赞 +1，原子更新 */
    @Update("UPDATE tb_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incLikeCount(Long commentId);

    /** 取消点赞 -1，最小为 0，原子更新 */
    @Update("UPDATE tb_comment SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END WHERE id = #{commentId}")
    int decLikeCount(Long commentId);
}
```

#### 2) Service 改为调用原子更新，并统一返回类型
```90:106:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
public Result<String> likeComment(Long commentId) {
    int updated = commentMapper.incLikeCount(commentId);
    if (updated == 0) {
        return Result.error(MessageConstant.FAILED);
    }
    return Result.success(MessageConstant.SUCCESS);
}
```

```114:129:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
public Result<String> cancelLikeComment(Long commentId) {
    int updated = commentMapper.decLikeCount(commentId);
    if (updated == 0) {
        return Result.error(MessageConstant.FAILED);
    }
    return Result.success(MessageConstant.SUCCESS);
}
```

说明：
- 取消了“selectById → set → updateById”的写法，避免并发覆盖。
- `decLikeCount` 用 CASE 保证不出现负数。
- 方法签名统一为 `Result<String>`；若需消除 IDE 泛型警告，可在 `Result.success/error` 静态方法补充泛型。

---

### 前端改造

#### 1) API 已具备点赞/取消点赞（确认导出）
```173:180:vibe-music-client-main/src/api/system.ts
/** 点赞评论 */
export const likeComment = (commentId: number) => {
  return http<Result>('patch', `/comment/likeComment/${commentId}`)
}

/** 取消点赞评论 */
export const cancelLikeComment = (commentId: number) => {
  return http<Result>('patch', `/comment/cancelLikeComment/${commentId}`)
}
```

#### 2) 播放页评论模块：点赞/取消点赞切换 + 会话内去重
- 文件：`components/DrawerMusic/right.vue`
- 核心改动：引入 `cancelLikeComment`，增加 `likedCommentIds`，根据状态决定 +1 或 -1，并同步 UI。

```1:9:vibe-music-client-main/src/components/DrawerMusic/right.vue
<script setup lang="ts">
import type { SongDetail } from '@/api/interface'
import { ref, inject, type Ref, computed } from 'vue'
import { formatNumber } from '@/utils'
import coverImg from '@/assets/cover.png'
import { likeComment, cancelLikeComment, addSongComment, getSongDetail, deleteComment } from '@/api/system'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
```

```72:106:vibe-music-client-main/src/components/DrawerMusic/right.vue
// 本地记录当前会话已点赞的评论，避免无限点赞
const likedCommentIds = ref<Set<number>>(new Set())

// 处理点赞/取消点赞
const handleLike = async (comment: any) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }

  try {
    const hasLiked = likedCommentIds.value.has(comment.commentId)
    if (!hasLiked) {
      const res = await likeComment(comment.commentId)
      if (res.code === 0) {
        // +1 并标记
        if (songDetail.value && songDetail.value.comments) {
          const updated = songDetail.value.comments.map(item => {
            if (item.commentId === comment.commentId) {
              return { ...item, likeCount: (item.likeCount || 0) + 1 }
            }
            return item
          })
          songDetail.value = { ...songDetail.value, comments: updated }
        }
        likedCommentIds.value.add(comment.commentId)
        ElMessage.success('点赞成功')
      }
    } else {
      const res = await cancelLikeComment(comment.commentId)
      if (res.code === 0) {
        // -1 并取消标记
        if (songDetail.value && songDetail.value.comments) {
          const updated = songDetail.value.comments.map(item => {
            if (item.commentId === comment.commentId) {
              const next = Math.max(0, (item.likeCount || 0) - 1)
              return { ...item, likeCount: next }
            }
            return item
          })
          songDetail.value = { ...songDetail.value, comments: updated }
        }
        likedCommentIds.value.delete(comment.commentId)
        ElMessage.success('已取消点赞')
      }
    }
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}
```

---

### 测试要点

- 点赞一次后再次点击，会变为取消点赞；计数在 0 与 1 之间切换，刷新后状态与后端一致。
- 并发连点：后端采用原子更新，不会出现越界或错乱；取消点赞不会低于 0。
- 未登录点击提示登录；登录后正常可用。

---

### 记录与影响范围
- 影响表：`tb_comment.like_count`（已存在）。
- 影响接口：`PATCH /comment/likeComment/{id}`、`PATCH /comment/cancelLikeComment/{id}`。
- 影响模块：播放页面右侧评论区 UI 与交互；后端 `CommentMapper`、`CommentServiceImpl`。

“歌单详情页评论”复用同样逻辑，只需使用相同的 `handleLike` 实现（当前组件已统一）。

```
/ 会话内记录已点赞的评论，避免重复 +1
const likedCommentIds = ref<Set<number>>(new Set())

// 处理点赞/取消点赞
const handleLike = async (comment: PlaylistComment) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }

  try {
    const hasLiked = likedCommentIds.value.has(comment.commentId)
    if (!hasLiked) {
      const res = await likeComment(comment.commentId)
      if (res.code === 0) {
        const updatedComments = comments.value.map(item => {
          if (item.commentId === comment.commentId) {
            return { ...item, likeCount: (item.likeCount || 0) + 1 }
          }
          return item
        })
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: updatedComments
        })
        likedCommentIds.value.add(comment.commentId)
        ElMessage.success('点赞成功')
      }
    } else {
      const res = await cancelLikeComment(comment.commentId)
      if (res.code === 0) {
        const updatedComments = comments.value.map(item => {
          if (item.commentId === comment.commentId) {
            const next = Math.max(0, (item.likeCount || 0) - 1)
            return { ...item, likeCount: next }
          }
          return item
        })
        playlistStore.setPlaylistInfo({
          ...playlistStore.playlist!,
          comments: updatedComments
        })
        likedCommentIds.value.delete(comment.commentId)
        ElMessage.success('已取消点赞')
      }
    }
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}
```

