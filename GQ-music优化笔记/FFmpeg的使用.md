### 目标
复用本项目的 FFmpeg 实践，在你的单体 Spring Boot 项目中快速完成：
- 视频上传后合并分片
- 获取视频时长
- HEVC(H.265) → H.264 兼容转码
- HLS 切片（m3u8 + ts）转码
- 生成首帧缩略图（可扩展多尺寸）

本文基于本项目现有实现的可复用经验与关键代码。

### 总体架构与流程
1) 上传阶段：分片上传 → 合并为完整视频文件
2) 媒体信息：用 ffprobe 获取时长、编码信息
3) 兼容处理：HEVC 视频转为 H.264（libx264）
4) 转封装与切片：mp4 → index.ts → 切片成多段 ts 与 m3u8（HLS）
5) 缩略图：生成首帧或指定时间点截图（可多尺寸）
6) 产物存储：本地/MinIO 对象存储；并记录 DB

核心类与位置
- 转码工具：`com.easylive.utils.FFmpegUtils`
- 子进程执行：`com.easylive.utils.ProcessUtils`
- 转码管道：`easylive-cloud-resource` 的 `TransferFileComponent`
- 配置：`com.easylive.entity.config.AppConfig`（`showFFmpegLog` 控制日志）

---

### 关键能力与命令

#### 1) 获取视频编码（ffprobe）
```java
public String getVideoCodec(String videoFilePath) {
  final String CMD_GET_CODE = "ffprobe -v error -select_streams v:0 -show_entries stream=codec_name \"%s\"";
  String cmd = String.format(CMD_GET_CODE, videoFilePath);
  String result = ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
  result = result.replace("\n", "");
  result = result.substring(result.indexOf("=") + 1);
  String codec = result.substring(0, result.indexOf("["));
  return codec;
}
```
引用:
```37:45:com/easylive/utils/FFmpegUtils.java
```

用途：
- 判断是否为 HEVC（H.265），若是先转为 H.264。

#### 2) HEVC(H.265) → H.264 转码（ffmpeg）
```java
public void convertHevc2Mp4(String src, String dst) {
  String CMD_HEVC_264 = "ffmpeg -i %s -c:v libx264 -crf 20 %s";
  String cmd = String.format(CMD_HEVC_264, src, dst);
  ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
}
```
引用:
```47:51:com/easylive/utils/FFmpegUtils.java
```

实践建议：
- `-crf 20` 质量/体积平衡可根据业务调整（一般 18-23）。

#### 3) HLS 切片（m3u8 + ts）
管道实现：
```java
public void convertVideo2Ts(File tsFolder, String videoFilePath) {
  final String CMD_TRANSFER_2TS = "ffmpeg -y -i \"%s\"  -vcodec copy -acodec copy -bsf:v h264_mp4toannexb \"%s\"";
  final String CMD_CUT_TS = "ffmpeg -i \"%s\" -c copy -map 0 -f segment -segment_list \"%s\" -segment_time 10 %s/%%4d.ts";
  String tsPath = tsFolder + "/" + Constants.TS_NAME;

  // 1) MP4 → index.ts（保证 H264 annex-b 格式）
  String cmd = String.format(CMD_TRANSFER_2TS, videoFilePath, tsPath);
  ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());

  // 2) index.ts → m3u8 + ts 切片（10s 一段）
  cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath());
  ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());

  // 3) 删除中间 index.ts
  new File(tsPath).delete();
}
```
引用:
```53:65:com/easylive/utils/FFmpegUtils.java
```

分段时长可调：
- `-segment_time 10` 改为你希望的切片长度（如 6/4 秒）。

#### 4) 获取视频时长（秒）
```java
public Integer getVideoInfoDuration(String video) {
  final String CMD = "ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"%s\"";
  String cmd = String.format(CMD, video);
  String result = ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
  if (StringTools.isEmpty(result)) return 0;
  result = result.replace("\n", "");
  return new BigDecimal(result).intValue();
}
```
引用:
```68:77:com/easylive/utils/FFmpegUtils.java
```

#### 5) 生成图片缩略图（首帧缩略/固定宽度）
现有方法（图片缩略图，200 宽等比）：
```java
public void createImageThumbnail(String filePath) {
  final String CMD = "ffmpeg -i \"%s\" -vf scale=200:-1 \"%s\"";
  String cmd = String.format(CMD, filePath, filePath + Constants.IMAGE_THUMBNAIL_SUFFIX);
  ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
}
```
引用:
```24:28:com/easylive/utils/FFmpegUtils.java
```

若要“视频首帧缩略图”，可扩展（示例）：
- 指定取第 1 秒（避免纯黑首帧）：`-ss 00:00:01 -vframes 1`
- 并设置输出尺寸（等比）：
```bash
ffmpeg -ss 00:00:01 -i "input.mp4" -vframes 1 -vf "scale=320:-1" "first_frame.jpg"
```
可在 `FFmpegUtils` 增加：
```java
public void createVideoFirstFrame(String videoPath, String jpgOut, int width) {
  String CMD = "ffmpeg -ss 00:00:01 -i \"%s\" -vframes 1 -vf scale=%d:-1 \"%s\"";
  String cmd = String.format(CMD, videoPath, width, jpgOut);
  ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
}
```

---

### 端到端样例：上传→合并→转码→切片→缩略图
参考 `TransferFileComponent.transferVideoFile`：
```java
// 1) 合并分片（完整视频）
String completeVideo = targetDir + "/index.mp4";
union(targetDir, completeVideo, true);

// 2) 时长/大小记录
int duration = fFmpegUtils.getVideoInfoDuration(completeVideo);

// 3) 兼容性：HEVC → H264
String codec = fFmpegUtils.getVideoCodec(completeVideo);
if ("hevc".equalsIgnoreCase(codec) /* 示例 */) {
  String tmp = completeVideo + ".tmp";
  new File(completeVideo).renameTo(new File(tmp));
  fFmpegUtils.convertHevc2Mp4(tmp, completeVideo);
  new File(tmp).delete();
}

// 4) HLS 切片（m3u8+ts）
fFmpegUtils.convertVideo2Ts(new File(targetDir), completeVideo);

// 5) 首帧缩略图（可选）
fFmpegUtils.createVideoFirstFrame(completeVideo, targetDir + "/cover.jpg", 320);

// 6) 清理/上传产物（本地或 MinIO）
```
引用位置：
```138:156:easylive-server/easylive-cloud/easylive-cloud-resource/src/main/java/com/easylive/component/TransferFileComponent.java
```

---

### 子进程与日志（防阻塞）
`ProcessUtils.executeCommand` 做了两件关键事：
- 同时消费子进程的标准输出/错误输出，避免缓冲区阻塞导致卡死
- `showFFmpegLog` 控制是否打印完整命令与输出

引用：
```17:56:com/easylive/utils/ProcessUtils.java
```

建议：
- 生产环境可关闭 `showFFmpegLog`，仅在问题排查时打开。
- 遇到长视频转码，建议异步执行（线程池/MQ），避免阻塞请求线程。

---

### 最佳实践与参数建议
- 画质与体积：`-crf`（x264）决定质量/体积，范围 18~28 区间选型
- 关键帧间隔：如需严谨 HLS 切片对齐，可加 `-g` 参数或重新编码而非 `-c copy`
- 音频：当前用 `-acodec copy`，若遇到不兼容可改为 `-c:a aac -b:a 128k`
- 首帧时间：首帧可能是黑帧，通常使用 `-ss 1` 或 `-ss 0.5` 再 `-vframes 1`
- 失败重试：捕获异常后可重试一次或记失败状态，避免任务丢失

---

### 在你的单体项目落地步骤
1) 准备环境：安装 ffmpeg/ffprobe，保证命令可在系统 PATH 直接调用
2) 复制工具类：
   - `ProcessUtils`（完整拷贝或等价实现）
   - `FFmpegUtils`（按需增改：首帧截图函数/多分辨率输出）
3) 在你的 Service/Component 中编排流程（见“端到端样例”）
4) 在配置中加入 `showFFmpegLog` 开关，便于排查问题
5) 若需要对象存储（MinIO/OSS）：在切片结束后上传产物并清理本地

如你把目标项目的包结构发给我，我可以直接把 `FFmpegUtils`、`ProcessUtils` 和一个 `TranscodeService` 骨架按你的项目名拷贝并改好命名空间，连同示例命令参数一起放进去。

FFmpegUtils

```
package com.easylive.utils;

import com.easylive.entity.config.AppConfig;
import com.easylive.entity.constants.Constants;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;

@Component
public class FFmpegUtils {

    @Resource
    private AppConfig appConfig;


    /**
     * 生成图片缩略图
     *
     * @param filePath
     * @return
     */
    public void createImageThumbnail(String filePath) {
        final String CMD_CREATE_IMAGE_THUMBNAIL = "ffmpeg -i \"%s\" -vf scale=200:-1 \"%s\"";
        String cmd = String.format(CMD_CREATE_IMAGE_THUMBNAIL, filePath, filePath + Constants.IMAGE_THUMBNAIL_SUFFIX);
        ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
    }


    /**
     * 获取视频编码
     *
     * @param videoFilePath
     * @return
     */
    public String getVideoCodec(String videoFilePath) {
        final String CMD_GET_CODE = "ffprobe -v error -select_streams v:0 -show_entries stream=codec_name \"%s\"";
        String cmd = String.format(CMD_GET_CODE, videoFilePath);
        String result = ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
        result = result.replace("\n", "");
        result = result.substring(result.indexOf("=") + 1);
        String codec = result.substring(0, result.indexOf("["));
        return codec;
    }

    public void convertHevc2Mp4(String newFileName, String videoFilePath) {
        String CMD_HEVC_264 = "ffmpeg -i %s -c:v libx264 -crf 20 %s";
        String cmd = String.format(CMD_HEVC_264, newFileName, videoFilePath);
        ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
    }

    public void convertVideo2Ts(File tsFolder, String videoFilePath) {
        final String CMD_TRANSFER_2TS = "ffmpeg -y -i \"%s\"  -vcodec copy -acodec copy -bsf:v h264_mp4toannexb \"%s\"";
        final String CMD_CUT_TS = "ffmpeg -i \"%s\" -c copy -map 0 -f segment -segment_list \"%s\" -segment_time 10 %s/%%4d.ts";
        String tsPath = tsFolder + "/" + Constants.TS_NAME;
        //生成.ts
        String cmd = String.format(CMD_TRANSFER_2TS, videoFilePath, tsPath);
        ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
        //生成索引文件.m3u8 和切片.ts
        cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath());
        ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
        //删除index.ts
        new File(tsPath).delete();
    }


    public Integer getVideoInfoDuration(String completeVideo) {
        final String CMD_GET_CODE = "ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"%s\"";
        String cmd = String.format(CMD_GET_CODE, completeVideo);
        String result = ProcessUtils.executeCommand(cmd, appConfig.getShowFFmpegLog());
        if (StringTools.isEmpty(result)) {
            return 0;
        }
        result = result.replace("\n", "");
        return new BigDecimal(result).intValue();
    }
}

```

ProcessUtils

```
package com.easylive.utils;

import com.easylive.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessUtils {
    private static final Logger logger = LoggerFactory.getLogger(ProcessUtils.class);

    private static final String osName = System.getProperty("os.name").toLowerCase();

    public static String executeCommand(String cmd, Boolean showLog) throws BusinessException {
        if (StringTools.isEmpty(cmd)) {
            return null;
        }

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            //判断操作系统
            if (osName.contains("win")) {
                process = Runtime.getRuntime().exec(cmd);
            } else {
                process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            }
            // 执行ffmpeg指令
            // 取出输出流和错误流的信息
            // 注意：必须要取出ffmpeg在执行命令过程中产生的输出信息，如果不取的话当输出流信息填满jvm存储输出留信息的缓冲区时，线程就回阻塞住
            PrintStream errorStream = new PrintStream(process.getErrorStream());
            PrintStream inputStream = new PrintStream(process.getInputStream());
            errorStream.start();
            inputStream.start();
            // 等待ffmpeg命令执行完
            process.waitFor();
            // 获取执行结果字符串
            String result = errorStream.stringBuffer.append(inputStream.stringBuffer + "\n").toString();
            // 输出执行的命令信息
            if (showLog) {
                logger.info("执行命令{}结果{}", cmd, result);
            }
            return result;
        } catch (Exception e) {
            logger.error("执行命令失败cmd{}失败:{} ", cmd, e.getMessage());
            throw new BusinessException("视频转换失败");
        } finally {
            if (null != process) {
                ProcessKiller ffmpegKiller = new ProcessKiller(process);
                runtime.addShutdownHook(ffmpegKiller);
            }
        }
    }

    /**
     * 在程序退出前结束已有的FFmpeg进程
     */
    private static class ProcessKiller extends Thread {
        private Process process;

        public ProcessKiller(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            this.process.destroy();
        }
    }


    /**
     * 用于取出ffmpeg线程执行过程中产生的各种输出和错误流的信息
     */
    static class PrintStream extends Thread {
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuffer stringBuffer = new StringBuffer();

        public PrintStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                if (null == inputStream) {
                    return;
                }
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuffer.append(line);
                }
            } catch (Exception e) {
                logger.error("读取输入流出错了！错误信息：" + e.getMessage());
            } finally {
                try {
                    if (null != bufferedReader) {
                        bufferedReader.close();
                    }
                    if (null != inputStream) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    logger.error("调用PrintStream读取输出流后，关闭流时出错！");
                }
            }
        }
    }
}

```

TransferFileComponent

```
package com.easylive.component;

import com.easylive.api.consumer.VideoClient;
import com.easylive.entity.config.AppConfig;
import com.easylive.entity.constants.Constants;
import com.easylive.entity.dto.UploadingFileDto;
import com.easylive.entity.enums.VideoFileTransferResultEnum;
import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.exception.BusinessException;
import com.easylive.utils.FFmpegUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.RandomAccessFile;

@Component
@Slf4j
public class TransferFileComponent {

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    @Resource
    private FFmpegUtils fFmpegUtils;

    @Resource
    private VideoClient videoClient;

    @Resource
    private ObjectStorageClient objectStorageClient;

    public void transferVideoFile(VideoInfoFilePost videoInfoFile) {
        VideoInfoFilePost updateFilePost = new VideoInfoFilePost();
        try {
            UploadingFileDto fileDto = redisComponent.getUploadingVideoFile(videoInfoFile.getUserId(), videoInfoFile.getUploadId());
            /**
             * 拷贝文件到正式目录
             */
            String tempFilePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_FOLDER_TEMP + fileDto.getFilePath();

            File tempFile = new File(tempFilePath);

            String targetFilePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_VIDEO + fileDto.getFilePath();
            File taregetFile = new File(targetFilePath);
            if (!taregetFile.exists()) {
                taregetFile.mkdirs();
            }
            FileUtils.copyDirectory(tempFile, taregetFile);

            /**
             * 删除临时目录
             */
            FileUtils.forceDelete(tempFile);
            redisComponent.delVideoFileInfo(videoInfoFile.getUserId(), videoInfoFile.getUploadId());

            /**
             * 合并文件
             */
            String completeVideo = targetFilePath + Constants.TEMP_VIDEO_NAME;
            TransferFileComponent.union(targetFilePath, completeVideo, true);

            /**
             * 获取播放时长
             */
            Integer duration = fFmpegUtils.getVideoInfoDuration(completeVideo);
            updateFilePost.setDuration(duration);
            updateFilePost.setFileSize(new File(completeVideo).length());
            updateFilePost.setFilePath(Constants.FILE_VIDEO + fileDto.getFilePath());
            updateFilePost.setTransferResult(VideoFileTransferResultEnum.SUCCESS.getStatus());

            /**
             * ffmpeg切割文件
             */
            this.convertVideo2Ts(completeVideo);

            // 如果使用 MinIO，将生成的切片目录上传
            if ("minio".equalsIgnoreCase(appConfig.getStorageProvider())) {
                String prefix = Constants.FILE_VIDEO + fileDto.getFilePath() + "/";
                try {
                    objectStorageClient.putDirectory(targetFilePath, Constants.FILE_FOLDER + prefix);
                    // 上传完成后可选择删除本地目录
                    FileUtils.deleteDirectory(new File(targetFilePath));
                } catch (Exception e) {
                    log.error("上传视频目录到对象存储失败", e);
                }
            }
        } catch (Exception e) {
            log.error("文件转码失败", e);
            updateFilePost.setTransferResult(VideoFileTransferResultEnum.FAIL.getStatus());
        } finally {
            videoClient.transferVideoFile4Db(videoInfoFile.getVideoId(), videoInfoFile.getUploadId(), videoInfoFile.getUserId(), updateFilePost);
        }
    }

    public static void union(String dirPath, String toFilePath, boolean delSource) throws BusinessException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new BusinessException("目录不存在");
        }
        File fileList[] = dir.listFiles();
        File targetFile = new File(toFilePath);
        try (RandomAccessFile writeFile = new RandomAccessFile(targetFile, "rw")) {
            byte[] b = new byte[1024 * 10];
            for (int i = 0; i < fileList.length; i++) {
                int len = -1;
                //创建读块文件的对象
                File chunkFile = new File(dirPath + File.separator + i);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(b)) != -1) {
                        writeFile.write(b, 0, len);
                    }
                } catch (Exception e) {
                    log.error("合并分片失败", e);
                    throw new BusinessException("合并文件失败");
                } finally {
                    readFile.close();
                }
            }
        } catch (Exception e) {
            throw new BusinessException("合并文件" + dirPath + "出错了");
        } finally {
            if (delSource) {
                for (int i = 0; i < fileList.length; i++) {
                    fileList[i].delete();
                }
            }
        }
    }

    private void convertVideo2Ts(String videoFilePath) {
        File videoFile = new File(videoFilePath);
        //创建同名切片目录
        File tsFolder = videoFile.getParentFile();
        String codec = fFmpegUtils.getVideoCodec(videoFilePath);
        //转码
        if (Constants.VIDEO_CODE_HEVC.equals(codec)) {
            String tempFileName = videoFilePath + Constants.VIDEO_CODE_TEMP_FILE_SUFFIX;
            new File(videoFilePath).renameTo(new File(tempFileName));
            fFmpegUtils.convertHevc2Mp4(tempFileName, videoFilePath);
            new File(tempFileName).delete();
        }

        //视频转为ts
        fFmpegUtils.convertVideo2Ts(tsFolder, videoFilePath);

        //删除视频文件
        videoFile.delete();
    }
}

```

