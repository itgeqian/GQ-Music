## todo33评论区父子结构开发

#### 目标
- 实现歌曲、专辑、歌单评论的父子结构与回复功能，前后端联动，保证发布/删除/回复后前端实时刷新展示。

## 数据与约定
- 评论表关键字段：`type`（0=歌曲/1=歌单/2=专辑）、`p_comment_id`（父评论ID，0 表示一级）、`reply_user_id`（被@的用户）、`img_path`（评论图片）、`like_count`。
- 父子结构规则：
  - 一级评论：`p_comment_id = 0`
  - 二级评论：`p_comment_id = 父评论的 commentId`
  - 回复时统一记录 `reply_user_id` 便于前端展示“回复 @xxx”
- 删除规则：
  - 删除父评论时，先删其子评论，再删父评论（两次 DELETE 是设计预期）

## 后端关键实现

- 添加歌曲评论（自动识别回复层级并记录被@用户；写入后清除 `songCache`）
```45:80:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Override
@CacheEvict(cacheNames = "songCache", allEntries = true)
public Result<String> addSongComment(CommentSongDTO commentSongDTO) {
    Map<String, Object> map = ThreadLocalUtil.get();
    Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
    Long userId = TypeConversionUtil.toLong(userIdObj);

    Comment comment = new Comment();
    Long replyId = commentSongDTO.getReplyCommentId();
    Long pId = 0L;
    Long replyUserId = null;
    if (replyId != null) {
        Comment parent = commentMapper.selectById(replyId);
        if (parent != null) {
            pId = parent.getPCommentId() == null || parent.getPCommentId() == 0 ? parent.getCommentId() : parent.getPCommentId();
            // 无论回复的是一级还是二级，都记录被@的用户
            replyUserId = parent.getUserId();
        }
    }
    comment.setUserId(userId)
            .setSongId(commentSongDTO.getSongId())
            .setPlaylistId(null)
            .setAlbumId(null)
            .setContent(commentSongDTO.getContent())
            .setImgPath(commentSongDTO.getImgPath())
            .setType(0)
            .setPCommentId(pId)
            .setReplyUserId(replyUserId)
            .setTopType(0)
            .setCreateTime(LocalDateTime.now()).setLikeCount(0L);

    if (commentMapper.insert(comment) == 0) {
        return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
    }
    return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
}
```

- 查询评论列表（返回已拼好的树/或平铺，前端做兜底构树）
```169:174:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
/** 获取歌曲评论列表 */
@Override
public Result<java.util.List<CommentVO>> getSongComments(Long songId) {
    java.util.List<CommentVO> list = commentMapper.getSongCommentsWithChildren(songId);
    return Result.success(list);
}
```

- 删除评论（父评论将先删子评论，再删本条；清理 `songCache`/`playlistCache`/`albumCache`）
```221:255:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "playlistCache", "albumCache"}, allEntries = true)
public Result<String> deleteComment(Long commentId) {
    Map<String, Object> map = ThreadLocalUtil.get();
    String role = map == null ? null : (String) map.get(JwtClaimsConstant.ROLE);
    Long userId = null;
    boolean isAdmin = RoleEnum.ADMIN.getRole().equals(role);
    if (!isAdmin) {
        Object userIdObj = map == null ? null : map.get(JwtClaimsConstant.USER_ID);
        if (userIdObj == null) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }
        userId = TypeConversionUtil.toLong(userIdObj);
    }

    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) {
        return Result.error(MessageConstant.NOT_FOUND);
    }
    if (!isAdmin && !Objects.equals(comment.getUserId(), userId)) {
        return Result.error(MessageConstant.NO_PERMISSION);
    }

    // 若为父级评论，连同其子级一并删除
    if (comment.getPCommentId() == null || comment.getPCommentId() == 0) {
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Comment>()
                .eq("p_comment_id", commentId));
    }

    if (commentMapper.deleteById(commentId) == 0) {
        return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
    }
    return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
}
```

- 点赞/取消点赞（计数增减；已在前端增加幂等态持久化，避免刷新后误判重复点赞）
```189:213:src/main/java/cn/edu/seig/vibemusic/service/impl/CommentServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
public Result<String> likeComment(Long commentId) {
    int updated = commentMapper.incLikeCount(commentId);
    if (updated == 0) return Result.error(MessageConstant.FAILED);
    return Result.success(MessageConstant.SUCCESS);
}

@Override
@CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
public Result<String> cancelLikeComment(Long commentId) {
    int updated = commentMapper.decLikeCount(commentId);
    if (updated == 0) return Result.error(MessageConstant.FAILED);
    return Result.success(MessageConstant.SUCCESS);
}
```

## 前端关键实现

### 专辑页：构树与回复
- 构树方法（后端若平铺返回，前端兜底转树）
```83:100:vibe-music-client-main/src/pages/album/[id].vue
function buildTree(list:any[]) {
  if (!Array.isArray(list)) return []
  const byId: Record<number, any> = {}
  const roots: any[] = []
  list.forEach(it => {
    // 归一字段：后端可能返回 pcommentId（小写c）
    const pidRaw = (it as any).pCommentId ?? (it as any).pcommentId ?? 0
    it.pCommentId = pidRaw
    if (!Array.isArray(it.children)) it.children = []
    byId[it.commentId] = it
  })
  list.forEach(it => {
    const pid = Number((it as any).pCommentId || 0)
    if (pid > 0 && byId[pid]) byId[pid].children.push(it)
    else if (pid === 0) roots.push(it)
  })
  return roots
}
```

- 拉取评论并归一/构树
```102:118:vibe-music-client-main/src/pages/album/[id].vue
const fetchAlbumComments = async () => {
  const res: any = await getAlbumComments(albumId.value)
  if (res?.code === 0) {
    const list = (res.data || []) as any[]
    const norm = list.map((it:any)=>({
      ...it,
      pCommentId: it.pCommentId ?? it.pcommentId ?? 0,
      children: Array.isArray(it.children) ? it.children : []
    }))
    const hasChildren = norm.some((it:any)=>Array.isArray(it.children) && it.children.length)
    const hasFlatChild = norm.some((it:any)=>Number(it.pCommentId||0) > 0)
    comments.value = hasChildren ? norm : (hasFlatChild ? buildTree(norm) : norm)
  }
}
```

- 二级回复提交（专辑）
```275:281:vibe-music-client-main/src/pages/album/[id].vue
async function submitAlbumReply(payload: { content: string; imgPath?: string; replyCommentId?: number }) {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
  if (!payload.content && !payload.imgPath) { ElMessage.warning('请输入评论内容或上传图片'); return }
  const res = await addAlbumComment({ albumId: albumId.value, content: payload.content, imgPath: payload.imgPath, replyCommentId: payload.replyCommentId })
  if (res.code === 0) { ElMessage.success('回复成功'); await fetchAlbumComments() } else { ElMessage.error(res.message || '回复失败') }
}
```

- 删除回调（专辑）
```412:415:vibe-music-client-main/src/pages/album/[id].vue
<CommentThread :list="comments" :on-reply="submitAlbumReply" :current-username="currentUsername"
  @like="handleLike"
  @profile="(uid:number)=>router.push(`/profile/${uid}`)"
  @delete="async (id:number)=>{ const res = await deleteComment(id); if(res.code===0){ ElMessage.success('删除成功'); await fetchAlbumComments() } else { ElMessage.error(res.message||'删除失败') } }" />
```

### 歌曲抽屉：与专辑/歌单同构的刷新与回复
- 归一/构树与刷新（改用独立 `getSongComments`，避免命中 `getSongDetail` 的缓存）
```116:152:vibe-music-client-main/src/components/DrawerMusic/right.vue
function normalizeComments(list: any[]): any[] {
  if (!Array.isArray(list)) return []
  const byId: Record<number, any> = {}
  const roots: any[] = []
  list.forEach((it:any)=>{
    it.pCommentId = it.pCommentId ?? it.pcommentId ?? 0
    it.children = Array.isArray(it.children) ? it.children : []
    byId[it.commentId] = it
  })
  list.forEach((it:any)=>{
    const pid = Number(it.pCommentId||0)
    if (pid>0 && byId[pid]) byId[pid].children.push(it)
    else if (pid===0) roots.push(it)
  })
  return roots
}

async function refreshComments(sid: number) {
  try {
    const res: any = await getSongComments(sid)
    if (res?.code === 0) {
      const list = Array.isArray(res.data) ? res.data : []
      const norm = list.map((it:any)=>({
        ...it,
        pCommentId: it.pCommentId ?? it.pcommentId ?? 0,
        children: Array.isArray(it.children) ? it.children : []
      }))
      const hasChildren = norm.some((it:any)=>Array.isArray(it.children) && it.children.length)
      const hasFlatChild = norm.some((it:any)=>Number(it.pCommentId||0) > 0)
      const tree = hasChildren ? norm : (hasFlatChild ? normalizeComments(norm) : norm)
      if ((songDetail as any)?.value) {
        ;(songDetail as any).value = { ...(songDetail as any).value, comments: tree }
      }
    }
  } catch {}
}
```

- 回复与删除（歌曲抽屉）
```328:336:vibe-music-client-main/src/components/DrawerMusic/right.vue
<CommentThread :list="comments" :on-reply="async ({content,imgPath,replyCommentId})=>{
  if(!userStore.isLoggedIn){ ElMessage.warning('请先登录'); return }
  if(!content && !imgPath){ ElMessage.warning('请输入评论内容或上传图片'); return }
  const sid = getSid(); if(!sid) return
  const res = await addSongComment({ songId: sid, content, imgPath, replyCommentId })
  if(res.code===0){ await refreshComments(sid) }
}" :current-username="currentUsername" @like="handleLike" @profile="gotoProfileFromDrawer" @delete="async (id:number)=>{ const res = await deleteComment(id); if(res.code===0){ ElMessage.success('删除成功'); const sid = getSid(); if(sid){ await refreshComments(sid) } } else { ElMessage.error('删除失败') } }" />
```

- 点赞/取消点赞（本地持久化已点赞集合，刷新页面后不误判）
```186:234:vibe-music-client-main/src/components/DrawerMusic/right.vue
const handleLike = async (comment: any) => {
  if (!userStore.isLoggedIn) { ElMessage.warning('请先登录'); return }
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
        saveLikedToStorage()
        ElMessage.success('点赞成功')
        const sid = getSid()
        if (sid) { await refreshComments(sid) }
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
        saveLikedToStorage()
        ElMessage.success('已取消点赞')
        const sid = getSid()
        if (sid) { await refreshComments(sid) }
      }
    }
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}
```

## 前端交互流总结
- 发布/回复成功 → 直接调用各自的评论列表接口（`getSongComments`/`getAlbumComments`/歌单对应）→ 归一/构树 → 覆盖页面的 `comments`。
- 删除成功 → 弹窗“删除成功” → 同步刷新评论列表。
- 点赞/取消点赞 → 本地先行更新计数与已点赞集合（并持久化）→ 再次请求评论列表保证树结构一致。

## 注意事项
- 为避免缓存导致的“未实时显示”，歌曲抽屉不再依赖带缓存的详情接口，而是直接拉评论列表。
- 回复时务必传 `replyCommentId`，后端会自动推断真实 `p_comment_id`（一级/二级都能正确归位）。
- 前端构树对字段名做了兼容（`pCommentId`/`pcommentId`），避免接口大小写差异带来的渲染问题。