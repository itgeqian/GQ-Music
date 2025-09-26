## todo6 歌手详情页“歌曲/专辑/详情/关注按钮”开发

#### 目标
- 在歌手详情页新增“歌曲 / 专辑 / 详情”三段导航。
- “关注”按钮移动到歌手简介下方（圆角）。
- 无歌曲/无专辑显示空状态文案。
- “详情”展示生平经历等长文本（与“简介”区分）。

#### 后端补充（为“详情”提供数据）
- VO 增加字段
```java
// src/main/java/cn/edu/seig/vibemusic/model/vo/ArtistDetailVO.java
private String detail; // 生平经历
```
- Mapper 返回 detail
```xml
<!-- src/main/resources/mapper/ArtistMapper.xml -->
<resultMap id="ArtistDetailVOResultMap" ...>
  <result column="detail" property="detail"/>
</resultMap>

<select id="getArtistDetailById" resultMap="ArtistDetailVOResultMap">
  SELECT a.id AS artistId, a.name AS artistName, ...,
         a.introduction AS introduction,
         a.detail       AS detail,        <!-- 新增 -->
         s.id AS songId, s.name AS songName, ...
  FROM tb_artist a
  LEFT JOIN tb_song s ON a.id = s.artist_id
  WHERE a.id = #{artistId}
</select>
```

#### 前端改造（vibe-music-client-main）

- Store 类型增加 detail（修复 TS 报错）
```ts
// src/stores/modules/artist.ts
interface ArtistInfo {
  artistId: number; artistName: string; avatar: string;
  birth: string; area: string; introduction: string;
  detail?: string;                // 新增
  songs: Song[];
}
```

- 拉取详情时赋值 detail
```ts
// src/pages/artist/[id].vue（片段）
artistStore.setArtistInfo({
  artistId: artistData.artistId,
  artistName: artistData.artistName || '未知歌手',
  avatar: artistData.avatar || '',
  birth: artistData.birth || '',
  area: artistData.area || '未知',
  introduction: artistData.introduction || '暂无简介',
  detail: artistData.detail || '',          // 新增
  songs: artistData.songs || []
})
```

- 过滤空歌曲行（解决“新增歌手无歌也渲染一行”）
```ts
// 过滤后端 LEFT JOIN 带出的空行
const validSongs = computed(() => {
  const list = artistInfo.value?.songs || []
  return list.filter((s: any) => s && s.songId && s.songName)
})
```

- 专辑按有效歌曲聚合
```ts
const albums = computed(() => {
  const songs = validSongs.value
  const map = new Map<string, any[]>()
  songs.forEach((s: any) => {
    const key = s.album || '未命名专辑'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(s)
  })
  return Array.from(map.entries()).map(([album, items]) => ({ album, items }))
})
```

- 关注按钮（简介下方 + 圆角）
```vue
<!-- src/pages/artist/[id].vue 歌手信息块内，简介下面 -->
<div class="mt-4">
  <el-button type="primary" plain size="small" @click="toggleFollow" class="rounded-full px-6">
    {{ following ? '已关注' : '关注' }}
  </el-button>
</div>
```

- 三段导航与内容区域
```vue
<!-- 导航 -->
<div class="mt-10 border-b mb-6 flex items-center gap-4">
  <button class="px-3 py-2 rounded-md" :class="activeTab==='songs' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='songs'">歌曲</button>
  <button class="px-3 py-2 rounded-md" :class="activeTab==='albums' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='albums'">专辑</button>
  <button class="px-3 py-2 rounded-md" :class="activeTab==='about' ? 'bg-activeMenuBg' : 'hover:bg-hoverMenuBg'" @click="activeTab='about'">详情</button>
</div>

<!-- 歌曲 -->
<div v-if="activeTab==='songs'">
  <div v-if="validSongs && validSongs.length > 0">
    <Table :data="validSongs" />
  </div>
  <div v-else class="w-full py-16 text-center text-muted-foreground">暂无歌曲</div>
</div>

<!-- 专辑（聚合展示） -->
<div v-else-if="activeTab==='albums'" class="flex flex-col gap-6">
  <template v-if="albums && albums.length > 0">
    <div v-for="(al, i) in albums" :key="i" class="border rounded-lg p-4">
      <h3 class="text-lg font-semibold mb-3">{{ al.album }}</h3>
      <Table :data="al.items" />
    </div>
  </template>
  <div v-else class="w-full py-16 text-center text-muted-foreground">暂无专辑</div>
</div>

<!-- 详情（长文本） -->
<div v-else class="space-y-3 text-sm text-muted-foreground">
  <p v-if="artistInfo?.artistName">姓名：{{ artistInfo.artistName }}</p>
  <p v-if="artistInfo?.birth">生日：{{ formatBirth(artistInfo.birth) }}</p>
  <p v-if="artistInfo?.area">地区：{{ artistInfo.area }}</p>
  <p v-if="artistInfo?.introduction">简介：</p>
  <p v-if="artistInfo?.introduction" class="whitespace-pre-line leading-7">{{ artistInfo.introduction }}</p>
  <p class="mt-2" v-if="artistInfo?.detail">详情：</p>
  <p v-if="artistInfo?.detail" class="whitespace-pre-line leading-7">{{ artistInfo.detail }}</p>
  <p v-else>暂无详情</p>
</div>
```

#### 空状态策略
- 歌曲：`validSongs.length === 0` → “暂无歌曲”
- 专辑：`albums.length === 0` → “暂无专辑”
- 详情：无 `detail` → “暂无详情”

#### 交互说明
- “关注”当前为前端占位（提示交互）。如需后端持久化，可扩展用户关注歌手接口与“喜欢页-关注歌手”分组。

