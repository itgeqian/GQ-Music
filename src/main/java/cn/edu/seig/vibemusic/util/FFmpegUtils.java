package cn.edu.seig.vibemusic.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/**
 * 极简 FFmpeg/ffprobe 工具
 */
@Component
public class FFmpegUtils {
    // reserved logger for troubleshooting
    private static final Logger log = LoggerFactory.getLogger(FFmpegUtils.class);

    @Value("${ffmpeg.show-log:false}")
    private boolean showLog;

    /** 获取视频编码（如 h264 / hevc） */
    public String getVideoCodec(String video) {
        String cmd = String.format("ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=nk=1:nw=1 \"%s\"", video);
        String res = ProcessUtils.execute(cmd, showLog);
        return res == null ? "" : res.trim();
    }

    /** 获取视频时长（秒，向下取整） */
    public int getDurationSeconds(String video) {
        String cmd = String.format("ffprobe -v error -show_entries format=duration -of default=nk=1:nw=1 \"%s\"", video);
        String res = ProcessUtils.execute(cmd, showLog);
        try {
            if (res == null || res.isBlank()) return 0;
            double d = Double.parseDouble(res.trim());
            return (int) Math.floor(d);
        } catch (Exception e) { return 0; }
    }

    /** 生成视频首帧缩略图（jpg），width 等比缩放 */
    public void createVideoFirstFrame(String video, String jpgOut, int width) {
        String cmd = String.format("ffmpeg -y -ss 00:00:01 -i \"%s\" -vframes 1 -vf scale=%d:-1 \"%s\"", video, width, jpgOut);
        ProcessUtils.execute(cmd, showLog);
    }
}


