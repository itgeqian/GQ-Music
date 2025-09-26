

## todo21FLAC 格式支持改造说明（后端 + 前端）

本次改造的目标：在保留 MP3 的同时，新增对 FLAC 的上传与时长解析支持，并保证客户端下载文件名扩展正确。

一、依赖声明
- 在 `pom.xml` 增加 jaudiotagger 3.x（中央仓库可用的坐标如下）：
```xml
<dependency>
  <groupId>net.jthink</groupId>
  <artifactId>jaudiotagger</artifactId>
  <version>3.0.1</version>
</dependency>
```
- 如使用镜像仓库（如阿里云）拉不到，可在 `pom.xml` 的 `<repositories>` 保留 Central：
```xml
<repositories>
  <repository>
    <id>aliyun</id>
    <url>https://maven.aliyun.com/repository/public</url>
  </repository>
  <repository>
    <id>central</id>
    <url>https://repo1.maven.org/maven2/</url>
  </repository>
</repositories>
```

——

二、后端：通用音频时长工具
文件：`src/main/java/cn/edu/seig/vibemusic/util/AudioDurationUtil.java`

作用：统一从 MultipartFile 提取时长，兼容 mp3 与 flac。mp3 优先用 mp3agic，其他格式通过反射调用 jaudiotagger（避免库缺失导致编译不通过）。

```12:61:src/main/java/cn/edu/seig/vibemusic/util/AudioDurationUtil.java
public final class AudioDurationUtil {

    public static String extractDurationSeconds(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) return null;
        File tempFile = null;
        try {
            String suffix = ".tmp";
            String original = audio.getOriginalFilename();
            if (original != null && original.lastIndexOf('.') > -1) {
                suffix = original.substring(original.lastIndexOf('.')).toLowerCase();
            }
            tempFile = File.createTempFile("vibe-audio-", suffix);
            audio.transferTo(tempFile);

            // mp3：使用 mp3agic
            if (suffix.endsWith(".mp3")) {
                Mp3File mp3File = new Mp3File(tempFile);
                long seconds = mp3File.getLengthInSeconds();
                return String.valueOf(seconds);
            }

            // 其他（含 .flac）：反射调用 jaudiotagger
            try {
                Class<?> audioFileIO = Class.forName("org.jaudiotagger.audio.AudioFileIO");
                Object af = audioFileIO.getMethod("read", File.class).invoke(null, tempFile);
                Object header = af.getClass().getMethod("getAudioHeader").invoke(af);
                Object len = header.getClass().getMethod("getTrackLength").invoke(header);
                int seconds = (len instanceof Integer) ? (Integer) len : Integer.parseInt(String.valueOf(len));
                if (seconds > 0) return String.valueOf(seconds);
            } catch (Throwable ignore) {}

            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }
}
```

——

三、后端：业务调用点
文件：`src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java`

改动：更新音频时，解析时长方法改为通用版本，并设置合理上限。

```552:569:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
song.setAudioUrl(audioUrl);
// 提取音频时长（秒）并写入 duration（支持 mp3、flac 等）
try {
    String seconds = cn.edu.seig.vibemusic.util.AudioDurationUtil.extractDurationSeconds(audioFile);
    if (seconds != null && !seconds.isEmpty()) {
        long s;
        try { s = Long.parseLong(seconds); } catch (NumberFormatException e) { s = -1L; }
        if (s < 0) s = Math.abs(s);
        long max = 4L * 60L * 60L; // 4 小时上限
        if (s > max) s = max;
        song.setDuration(String.valueOf(s));
    }
} catch (Exception ignored) {}
```

——

四、后端：接口入参白名单校验
文件：`src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java`
```463:474:src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java
@PatchMapping("/updateSongAudio/{id}")
public Result<String> updateSongAudio(@PathVariable("id") Long songId, @RequestParam("audio") MultipartFile audio) {
    // 服务端白名单校验
    String name = audio.getOriginalFilename();
    if (name != null) {
        String lower = name.toLowerCase();
        if (!(lower.endsWith(".mp3") || lower.endsWith(".flac"))) {
            return Result.error("仅支持 mp3 或 flac 音频");
        }
    }
    String audioUrl = minioService.uploadFile(audio, "songs");
    return songService.updateSongAudio(songId, audioUrl, audio);
}
```

——

五、管理端：上传控件放开 .flac
文件：`vibe-music-admin-main/src/views/song/form/upload.vue`

要点：accept 增加 .flac，校验与文案更新；预览不变。
```91:105:vibe-music-admin-main/src/views/song/form/upload.vue
<el-upload
  :file-list="fileList"
  :auto-upload="false"
  :limit="1"
  action="#"
  drag
  accept=".mp3,.flac,audio/mpeg,audio/flac"
  @change="handleChange"
>
  <div class="el-upload__text">
    ...
    点击或拖拽上传（支持 .mp3 / .flac）
  </div>
</el-upload>
```
```22:36:vibe-music-admin-main/src/views/song/form/upload.vue
const handleChange = file => {
  const name = file?.raw?.name?.toLowerCase?.() || "";
  if (!(name.endsWith('.mp3') || name.endsWith('.flac'))) {
    message("仅支持 .mp3 或 .flac 音频文件", { type: "warning" });
    fileList.value = [];
    return;
  }
  fileList.value = [file.raw];
  ...
};
```

——

六、客户端：下载文件名扩展自动推断
文件：`vibe-music-client-main/src/components/Table.vue`

改动：不再强制 `.mp3`，根据 `row.audioUrl` 提取扩展名（兼容 flac）。
```157:170:vibe-music-client-main/src/components/Table.vue
const url = row.audioUrl || ''
const extFromUrl = (() => {
  try {
    const u = new URL(url)
    const pathname = u.pathname || ''
    const m = pathname.match(/\.([a-zA-Z0-9]+)(?:\?|$)/)
    return m ? `.${m[1]}` : ''
  } catch {
    const m = url.match(/\.([a-zA-Z0-9]+)(?:\?|$)/)
    return m ? `.${m[1]}` : ''
  }
})()
const safeExt = extFromUrl && extFromUrl.length <= 6 ? extFromUrl : ''
const fileName = `${row.songName} - ${row.artistName}${safeExt || ''}`
```

——

七、联调与注意事项
- 浏览器解码：大部分浏览器原生支持 mp3；flac 播放依赖浏览器与 `<audio>` 解码能力，不支持时仅能下载或转码后播放。
- MinIO/对象存储：`contentType` 会沿用上传的 `file.getContentType()`，建议前端上传时让浏览器自动附带 `audio/flac`。
- 幂等与大小限制：后端已对时长做上限保护（默认 4 小时）；若需要限制音频大小，可在 Controller 增加大小校验或通过 Spring `multipart` 配置限制上传。

——

八、快速验证清单
- 上传 flac 成功，接口返回 200
- 数据库中 `duration` 已填入合理时长
- 管理端上传弹窗 accept 显示包含 `.flac`
- 客户端下载的文件名扩展与 `audioUrl` 一致

如需把 jaudiotagger 的反射调用改为“直接强依赖并使用”，我可以再给出去反射的版本；或你想支持 WAV/AAC 等，也只需要在工具类里按扩展名分支解析即可。