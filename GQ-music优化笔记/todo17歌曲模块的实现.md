## todo17歌词模块的实现

### 歌词模块开发文档（方案B：MinIO 存 .lrc）

#### 一、目标与方案
- 目标：为歌曲提供结构化歌词（随播放高亮/滚动、支持手动逐行预览）。
- 方案：.lrc 文件存 MinIO（桶 `vibe-music-data`，目录 `songLyrics/`），DB 仅存 `lyric_url`；后端解析 LRC 返回结构化时间轴；前端按进度渲染。

---

### 二、数据与配置
- 表结构：已新增 `tb_song.lyric_url`（保留 `lyric` 以兼容，可后续删除）。
```37:41:sql/2025-09-16_album_module.sql
-- 2.1) 歌曲表增加歌词URL字段（若不存在）
ALTER TABLE `tb_song`
  ADD COLUMN `lyric_url` varchar(512) NULL COMMENT '歌词文件URL' AFTER `audio_url`;
```
- 接口白名单：歌词获取为公开接口。
```20:31:src/main/java/cn/edu/seig/vibemusic/config/WebConfig.java
.excludePathPatterns(
        ...
        "/song/getAllSongs", "/song/getRecommendedSongs", "/song/getSongDetail/**", "/song/getSongsByAlbumId", "/song/getLyric/**",
        ...
);
```

---

### 三、后端实现

1) 读取歌词与解析
- 接口：`GET /song/getLyric/{songId}` → 返回 `List<LyricLine{ timeMs, text }>`
```63:71:src/main/java/cn/edu/seig/vibemusic/controller/SongController.java
@GetMapping("/getLyric/{songId}")
public Result<List<LyricLine>> getLyric(@PathVariable Long songId) {
    return songService.getLyric(songId);
}
```
- Service 接口
```26:33:src/main/java/cn/edu/seig/vibemusic/service/ISongService.java
// 获取歌词（结构化）
Result<List<LyricLine>> getLyric(Long songId);
```
- LRC 解析（支持 [mm:ss.x|xx|xxx]、多时间戳一行、[offset:ms]；缓存10分钟）
```319:367:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
@Cacheable(cacheNames = "lyricCache", key = "'lyric-' + #songId")
public Result<List<LyricLine>> getLyric(Long songId) { ... }

private List<LyricLine> parseLrc(String lrc) {
    ...
    // [offset:+/-ms]
    Matcher off = Pattern.compile("(?m)^\\[offset:(-?\\d+)]\\s*$").matcher(lrc);
    if (off.find()) globalOffset = Long.parseLong(off.group(1));

    // 允许毫秒 1-3 位；支持多时间戳一行
    Pattern p = Pattern.compile("(?m)^((?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?])+)(.*)$");
    Matcher m = p.matcher(lrc);
    while (m.find()) {
        String tsGroup = m.group(1);
        String text = m.group(2).trim();
        Matcher tm = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]").matcher(tsGroup);
        ...
        long ms = min * 60_000L + sec * 1_000L + extraMs;
        list.add(new LyricLine(ms + globalOffset, text));
    }
    list.sort(Comparator.comparingLong(LyricLine::getTimeMs));
    return list;
}
```

2) MinIO 读取与“智能解码”
- 优先 UTF-8；若中文得分更高或 UTF-8 出现大量替换符，则回退 GB18030；支持带 BOM。
```86:141:src/main/java/cn/edu/seig/vibemusic/service/impl/MinioServiceImpl.java
public String readText(String fileUrl) {
    ...
    byte[] bytes = in.readAllBytes();
    return smartDecode(bytes);
}

private String smartDecode(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && ... ) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }
    String utf8 = new String(bytes, StandardCharsets.UTF_8);
    int utf8Score = chineseScore(utf8) - replacementPenalty(utf8);

    String gb = new String(bytes, Charset.forName("GB18030"));
    int gbScore = chineseScore(gb) - replacementPenalty(gb);

    return gbScore > utf8Score ? gb : utf8;
}
```

3) 管理端上传歌词（可单独补传）
- 接口：`PATCH /admin/updateSongLyric/{id}`，表单字段 `lyric`（.lrc），上传后写入 `lyric_url` 并清理歌词缓存。
```463:469:src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java
@PatchMapping("/updateSongLyric/{id}")
public Result<String> updateSongLyric(@PathVariable("id") Long songId, @RequestParam("lyric") MultipartFile lyric) {
    String lyricUrl = minioService.uploadFile(lyric, "songLyrics");
    return songService.updateSongLyric(songId, lyricUrl);
}
```
```525:541:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
@CacheEvict(cacheNames = {"songCache", "lyricCache"}, key = "'lyric-' + #songId", allEntries = false)
public Result<String> updateSongLyric(Long songId, String lyricUrl) {
    Song song = songMapper.selectById(songId);
    ...
    song.setLyricUrl(lyricUrl);
    if (songMapper.updateById(song) == 0) return Result.error(...);
    return Result.success(...);
}
```

---

### 四、前端实现（客户端）

1) API
```262:271:vibe-music-client-main/src/api/system.ts
export const getLyric = (songId: number) => {
  return http<Result>('get', `/song/getLyric/${songId}`)
}
```

2) 组件 `LyricPanel.vue`
- 功能：拉取歌词、随播放高亮滚动、鼠标滚轮逐行预览（显示该行时间）、点击某行跳转播放；过滤空行。
```1:33:vibe-music-client-main/src/components/LyricPanel.vue
<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { getLyric } from '@/api/system'
import { useAudioPlayer } from '@/hooks/useAudioPlayer'
...
</script>
```
```8:18:vibe-music-client-main/src/components/LyricPanel.vue
const lines = ref<{ timeMs: number; text: string }[]>([])
...
const res: any = await getLyric(props.songId)
if (res?.code === 0 && Array.isArray(res.data)) {
  lines.value = (res.data as any[])
    .map(l => ({ timeMs: l.timeMs, text: String(l.text ?? '') }))
    .filter(l => l.text.trim().length > 0) // 去掉空行
}
```
```33:61:vibe-music-client-main/src/components/LyricPanel.vue
function findActiveIndex(ms: number) { ... }
function scrollToActive() { ... }
function seekTo(i: number) { ... }
function scrollToIndex(i: number) {
  const container = box.value
  const el = container?.querySelector(`[data-i="${i}"]`) as HTMLElement | null
  if (!container || !el) return
  // 将目标行滚动到容器顶部附近，确保每步有位移
  const paddingTop = 8
  const targetTop = Math.max(0, el.offsetTop - paddingTop)
  container.scrollTo({ top: targetTop, behavior: 'smooth' })
}
```
- 滚轮逐行预览 + 行内时间提示（左侧小徽标）
```62:109:vibe-music-client-main/src/components/LyricPanel.vue
const previewIndex = ref(0)
const showHint = ref(false)
const hintText = ref('00:00')
...
function stepPreview(direction: 1 | -1) {
  ...
  previewIndex.value = Math.min(lines.value.length - 1, Math.max(0, previewIndex.value + direction))
  scrollToIndex(previewIndex.value)
  hintText.value = formatTime(lines.value[previewIndex.value].timeMs)
  showHint.value = true
  ...
}
function onWheel(e: WheelEvent) {
  e.preventDefault()
  stepPreview(e.deltaY > 0 ? 1 : -1)
}
onMounted(() => { box.value?.addEventListener('wheel', onWheel, { passive: false }) })
```
- 随播放时间自动高亮滚动（手动滚动1s内暂停自动跟随）
```109:127:vibe-music-client-main/src/components/LyricPanel.vue
let holdAutoScrollUntil = 0
watch(() => props.currentTime, (t) => {
  if (Date.now() < holdAutoScrollUntil) return
  const ms = Math.floor(t * 1000)
  const idx = findActiveIndex(ms)
  if (idx !== active.value) {
    active.value = idx
    scrollToActive()
  }
})
```
- 模板（行内时间提示 + 文本）
```127:141:vibe-music-client-main/src/components/LyricPanel.vue
<div
  v-for="(l,i) in lines"
  :key="i"
  :data-i="i"
  class="relative text-center cursor-pointer transition px-1 py-1 pl-8 select-none"
  :class="i===active ? 'text-primary text-base font-semibold' : 'opacity-80 hover:opacity-100'"
  @click="seekTo(i)"
>
  <span v-show="showHint && i===previewIndex" class="absolute left-1 text-xs px-1.5 py-0.5 rounded bg-black/60 text-white select-none z-10">{{ hintText }}</span>
  {{ l.text }}
</div>
```

3) 集成位置（示例：播放器抽屉右侧）
```169:171:vibe-music-client-main/src/components/DrawerMusic/right.vue
<LyricPanel :songId="songDetail.songId" :currentTime="audio.currentTime.value" />
```

---

### 五、管理端实现

1) API
```225:235:vibe-music-admin-main/src/api/system.ts
export const updateSongAudio = (id: number, data: object) => { ... }
export const updateSongLyric = (id: number, data: FormData) => {
  return http.request<Result>("patch", `/admin/updateSongLyric/${id}`, { headers: { "Content-Type": "multipart/form-data", Authorization: userData.accessToken }, data })
}
```

2) UI（上传音频/歌词弹窗，支持仅补传歌词）
```68:109:vibe-music-admin-main/src/views/song/form/upload.vue
<el-dialog v-model="isVisible" title="上传音频/歌词" ...>
  <el-upload ... accept=".mp3" @change="handleChange">...</el-upload>
  <audio v-if="audioUrl" :src="audioUrl" controls class="mt-3" />
  <div class="mt-6">
    <div class="mb-2 text-sm text-gray-500">可选：上传歌词（.lrc）</div>
    <el-upload :file-list="lyricList" accept=".lrc,text/plain" @change="handleLyricChange">...</el-upload>
  </div>
  <template #footer>...</template>
</el-dialog>
```
```22:55:vibe-music-admin-main/src/views/song/form/upload.vue
const submitForm = async () => {
  if (!props.songId) { message("请先选择一条歌曲记录", { type: "warning" }); return; }
  if (!fileList.value.length && !lyricList.value.length) { message("请至少选择一个文件（音频或歌词）", { type: "warning" }); return; }
  try {
    if (fileList.value.length) { ... await updateSongAudio(...) }
    if (lyricList.value.length) { ... await updateSongLyric(...) }
    message("提交成功", { type: "success" }); emit("update:visible", false); emit("success");
  } catch (e) { message(String(e?.message || "提交失败，请重试"), { type: "error" }); }
}
```

---

### 六、使用与测试要点
- MinIO 直链：`http://localhost:9000/vibe-music-data/songLyrics/xxx.lrc`（或对象键 `songLyrics/xxx.lrc`，后端均可读）
- 编码：UTF-8 或 GBK/GB18030 均可（已“智能解码”）；建议统一 UTF-8（无 BOM）
- 空行：前端已过滤空行，不再显示“...”
- 缓存：歌词10分钟；上传新歌词后已自动清理缓存
- 手动预览：滚轮逐行、左侧显示该行时间；点击可 seek

---

### 七、后续可选优化
- 删除 `tb_song.lyric` 文本列（若不再需要）（已删）
- 显示双语/翻译：返回多轨歌词数组；UI 双列或合并渲染
- 行内字级逐字高亮（Enhanced LRC）
- 歌词编辑器（管理端在线编辑/校正时间轴）

如需我再输出“FAQ/排错指南”或“上线 checklist”，告诉我要点即可分批补充。

### 显示双语/翻译和行内字级逐字高亮的复杂程度

不复杂到“不要做”的程度，但两者的复杂度差距明显：

- 双语/翻译（多轨歌词）
  - 复杂度：低-中（约 2/5）
  - 后端：把当前返回的 `List<LyricLine>` 扩展为 `List<LyricTrack>`（每轨含 lang、name、lines）。也可继续用现结构，改为数组包多份轨道，基本是数据包装，解析仍复用。
  - 对齐策略：同时间轴合并（按 timeMs 归并、±100ms 视为同一行），或分别渲染两列（无需强对齐）。
  - 前端：`LyricPanel` 接收多轨；两种渲染模式可选
    - 双列模式：每列一条轨
    - 合并模式：主行+副行（副行小一号字显示在主行下）
  - 估时：0.5–1 天（含切换按钮、无翻译时的回退）

- 行内逐字高亮（Enhanced LRC）
  - 复杂度：中-偏高（约 4/5）
  - 解析：支持行内词级时间戳（不同来源格式不一，常见如 `<mm:ss.xx>` 或 `{xx}` 标记），需要把一行拆成多个 segment：`[{ text, startMs, endMs }]`，无标记时回退整行。
  - 渲染：
    - 根据 `currentTime` 在 active 行内定位当前词；切换词时要平滑过渡。
    - 两种实现：
      - 简单高亮：当前词加样式，前词常亮
      - 进度高亮：用 CSS mask/线性渐变 或对当前词计算占比（需 `requestAnimationFrame` 微调，注意性能）
    - 与滚动/预览交互：手动滚轮时暂停自动推进，切词/滚动的同步处理要谨慎。
  - 估时：2–3 天（含多格式测试、性能和边界处理）

建议
- 若追求“短平快”的体验升级，先做双语/翻译，多轨渲染落地快、风险低。
- 逐字高亮放次阶段，等有稳定的词级标注来源再上；或者先做“当前词高亮”的简化版，不做进度填充。

如果你决定先做双语，我可以直接给出最小改动方案（数据结构与前端渲染点位），半天内可落地。