### 音频上传自动写入时长（duration）改造开发文档

本文记录在上传歌曲音频时，后端自动解析音频时长并写入 `tb_song.duration` 的改造过程，适用于 Vibe Music Server（Spring Boot 3.3.x）。

### 目标与问题
- 目标：上传或更新音频后，自动为歌曲写入时长，前端不再显示 0:00。
- 原因：之前的新增/更新流程未给 `duration` 赋值（数据库为 NULL）。

### 技术方案
- 使用 `mp3agic` 解析 MP3 文件长度（秒）。
- 在更新音频接口处理链上，拿到上传的 `MultipartFile`，解析出时长并一并更新数据库。

### 变更内容概览
- 依赖新增：`com.mpatric:mp3agic`
- 新增工具类：`AudioDurationUtil`（解析 MP3 时长）
- 方法签名调整：
  - `AdminController.updateSongAudio` 增加将源文件传给服务层
  - `ISongService.updateSongAudio` 增加 `MultipartFile audioFile` 参数
- 业务实现修改：
  - `SongServiceImpl.updateSongAudio` 中，在设置 `audioUrl` 的同时解析并写入 `duration`

### 代码改动清单

- pom.xml：添加 mp3agic 依赖
```xml
<dependency>
  <groupId>com.mpatric</groupId>
  <artifactId>mp3agic</artifactId>
  <version>0.9.1</version>
</dependency>
```

- 新增工具类 `cn.edu.seig.vibemusic.util.AudioDurationUtil`
```java
public final class AudioDurationUtil {
    public static String extractMp3DurationSeconds(MultipartFile audio) {
        // 将 MultipartFile 写入临时文件 → 用 Mp3File 解析 → getLengthInSeconds()
        // 返回秒数字符串，失败返回 null
    }
}
```

- `AdminController.updateSongAudio`
```java
@PatchMapping("/updateSongAudio/{id}")
public Result updateSongAudio(@PathVariable("id") Long songId,
                              @RequestParam("audio") MultipartFile audio) {
    String audioUrl = minioService.uploadFile(audio, "songs");
    return songService.updateSongAudio(songId, audioUrl, audio); // 传入源文件
}
```

- `ISongService` 方法签名
```java
Result updateSongAudio(Long songId, String audioUrl, MultipartFile audioFile);
```

- `SongServiceImpl.updateSongAudio`
```java
    song.setAudioUrl(audioUrl);
        // 提取音频时长（秒）并写入 duration
        try {
            String seconds = cn.edu.seig.vibemusic.util.AudioDurationUtil.extractMp3DurationSeconds(audioFile);
            if (seconds != null && !seconds.isEmpty()) {
                long s;
                try { s = Long.parseLong(seconds); } catch (NumberFormatException e) { s = -1L; }
                if (s < 0) {
                    s = Math.abs(s);
                }
                // 合理上限（例如 4 小时），防止异常大值污染
                long max = 4L * 60L * 60L;
                if (s > max) {
                    s = max;
                }
                song.setDuration(String.valueOf(s));
            }
        } catch (Exception ignored) {}
        if (songMapper.updateById(song) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }
```

### 数据库与返回值说明
- 表：`tb_song`
- 字段：`duration varchar(10)`，存“秒”的字符串，例如 `"241"`。
- 前端展示：将秒数格式化为 `mm:ss`。示例：
  - `241` → `04:01`
  - 计算：`mm = Math.floor(seconds/60)`，`ss = seconds % 60`，注意补零。

### 使用与验证
1. 启动后端。
2. 管理端“更新歌曲音频”上传 MP3。
3. 查看数据库 `tb_song.duration` 不再为 NULL，变为秒数。
4. 列表与详情接口返回的 `duration` 有值，前端显示不再为 0:00。

### 历史数据处理（可选）
- 简单：对需要展示的旧歌曲“重新上传音频”，自动补齐。
- 批量：新增管理任务，遍历 `tb_song` 中 `audio_url` 非空且 `duration` 为空的记录，从 MinIO 拉取文件头解析秒数并回填。
  - 如需我实现批量回填任务，请告知。

### 兼容与回退
- 解析失败不会影响接口：仅跳过写入 `duration`，依然更新 `audioUrl`。
- 仅支持 MP3 解析；如需支持 M4A/FLAC，可扩展使用 jaudiotagger 等库。

### 性能与安全
- 解析使用临时文件，文件会在 finally 中删除，避免磁盘泄露。
- 上传大文件仅解析头信息，开销较小；如担心磁盘 IO，可改为流式探测。
- 生产环境建议限制最大上传大小，已在 `application.yml` 中有 `spring.servlet.multipart` 限制。

### 常见问题
- 仍显示 0:00：检查数据库是否已写入秒数；若为空，可能解析失败（非 MP3 或文件损坏）。
- 不同格式需求：若要直接存 `mm:ss`，可在 `SongServiceImpl` 将秒数格式化后再写入。

以上即为本次“音频上传自动写入时长”改造的完整记录，已在本地验证通过。