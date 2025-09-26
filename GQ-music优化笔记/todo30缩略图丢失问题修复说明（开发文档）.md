## todo30缩略图丢失问题修复说明（开发文档）

一、问题现象
- 默认进入“歌曲”Tab，然后切到“专辑”Tab：专辑块有封面，但分组下的歌曲缩略图不显示。
- 直接进入“专辑”Tab：专辑与分组内歌曲缩略图均正常。
- 从“专辑”切回“歌曲”：歌曲缩略图需要滚动页面后才显示；若歌曲数量少无滚动条，则无法显示。

二、根因分析
- 缩略图组件使用 Element Plus 的 `el-image` 懒加载（`lazy`）特性。
- 懒加载依赖 IntersectionObserver 监听“滚动容器”的可视区域。
- 在 Tab 切换时，内容 DOM 和/或滚动容器发生切换，Observer 的 root 不再正确，图片未进入“可视区域”判断，导致不触发加载；当列表不足以产生滚动条时，更不会触发。

三、解决思路
1) 取消懒加载对滚动容器的依赖：在歌手详情页里的 `Table` 统一关闭图片懒加载，使图片在首帧就加载。
2) 做好封面数据兜底：分组时，如果歌曲 `coverUrl` 为空，则回退到“分组封面”或“默认专辑图”；头像同样加本地兜底和 `onerror` 回退。

四、关键改动

1. `components/Table.vue` 增加开关参数，控制图片是否懒加载
```23:29:vibe-music-client-main/src/components/Table.vue
  clickableAlbum: { type: Boolean, default: true },
  // 图片懒加载时的滚动容器（可选）
  scrollContainer: { type: [Object, String] as PropType<HTMLElement | string | null>, default: undefined },
  // 是否启用图片懒加载（Tab 切换场景可关闭）
  imageLazy: { type: Boolean, default: true },
```

把 `el-image` 的 `lazy` 改为受控：
```371:379:vibe-music-client-main/src/components/Table.vue
          <div class="w-10 h-10 relative">
            <template v-if="row.coverUrl">
              <el-image
                :src="row.coverUrl"
                fit="cover"
                :lazy="props.imageLazy"
                :scroll-container="(props.scrollContainer as any) || undefined"
                :alt="row.songName"
                class="w-full h-full rounded-md"
              />
```

2. `artist/[id].vue` 关闭表格中的图片懒加载（歌曲 Tab 与专辑分组都关闭）
```262:268:vibe-music-client-main/src/pages/artist/[id].vue
<!-- 歌曲列表：禁用图片懒加载 -->
<Table :data="filteredSortedSongs" :clickable-artist="false" :image-lazy="false" />
```

```294:300:vibe-music-client-main/src/pages/artist/[id].vue
<!-- 专辑分组：禁用图片懒加载 -->
<Table
  :data="al.items.map((s:any)=>({ ...s, coverUrl: s.coverUrl || al.coverUrl || defaultAlbumCover }))"
  :clickable-artist="false"
  :image-lazy="false"
/>
```

3. 专辑分组数据兜底：为分组存封面，并回填给每首歌
```145:158:vibe-music-client-main/src/pages/artist/[id].vue
const albums = computed(() => {
  const songs = pageSongs.value
  type Group = { albumId?: number; album: string; items: any[]; coverUrl?: string }
  const map = new Map<string, Group>()
  songs.forEach((s: any) => {
    const idPart = (s.albumId != null && Number.isFinite(Number(s.albumId))) ? String(s.albumId) : ''
    const namePart = s.album || '未命名专辑'
    const key = idPart ? `id:${idPart}` : `name:${namePart}`
    if (!map.has(key)) map.set(key, { albumId: idPart ? Number(idPart) : undefined, album: namePart, items: [], coverUrl: s.coverUrl })
    const g = map.get(key)!
    if (!g.coverUrl && s.coverUrl) g.coverUrl = s.coverUrl
    // 歌曲封面兜底：分组封面 -> 默认封面
    const item = { ...s }
    if (!item.coverUrl) item.coverUrl = g.coverUrl || defaultAlbumCover
    g.items.push(item)
  })
  return Array.from(map.values())
})
```

4. 头像与专辑标题封面兜底及 onerror 回退
```205:210:vibe-music-client-main/src/pages/artist/[id].vue
<div class="w-48 h-48 rounded-full overflow-hidden bg-gray-200">
  <img :src="artistInfo?.avatar || defaultAvatar"
       :alt="artistInfo?.artistName"
       class="w-full h-full object-cover"
       @error="($event.target as HTMLImageElement).src = defaultAvatar" />
</div>
```

```287:293:vibe-music-client-main/src/pages/artist/[id].vue
<h3 class="text-lg font-semibold mb-3 flex items-center gap-3">
  <img :src="al.coverUrl || defaultAlbumCover"
       alt="cover"
       class="w-10 h-10 rounded object-cover"
       @error="($event.target as HTMLImageElement).src = defaultAlbumCover" />
  <a class="text-primary cursor-pointer hover:underline" @click="$router.push({ path: `/album/${al.albumId ?? ''}` })">{{ al.album }}</a>
</h3>
```

五、验证用例
- 直接打开“专辑”Tab：专辑封面 + 分组歌曲缩略图全部正常。
- 默认进入“歌曲”→ 切到“专辑”：分组歌曲缩略图无需滚动即可显示。
- 从“专辑”→ 切回“歌曲”：歌曲缩略图无需滚动即可显示（少量歌曲无滚动条也可见）。
- 弱网/跨域导致图片失败时，头像/封面用本地默认图回退。

六、可选优化
- 若后续要继续使用懒加载，可在每个 Tab 容器稳定后再启用 `imageLazy`，并明确传入正确的 `scrollContainer`（可通过 `ref` 定位容器元素）。
- 对于超长列表可结合 `ElTableV2` 虚拟滚动以进一步优化性能。

以上为本次“缩略图丢失”问题的修复说明与关键代码。如需导出为单独的文档，我可以放到 `docs/artist-thumbnails.md` 并提交。