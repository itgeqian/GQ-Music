package cn.edu.seig.vibemusic.util;

import com.mpatric.mp3agic.Mp3File;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 音频工具：提取音频时长（单位：秒，字符串形式）。
 * 优先兼容 MP3；对 FLAC/其它常见格式，使用 jaudiotagger 解析。
 */
public final class AudioDurationUtil {

    private AudioDurationUtil() {
    }

    /**
     * 通用：从 MultipartFile 提取时长，失败返回 null。
     */
    public static String extractDurationSeconds(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return null;
        }
        File tempFile = null;
        try {
            String suffix = ".tmp";
            String original = audio.getOriginalFilename();
            if (original != null && original.lastIndexOf('.') > -1) {
                suffix = original.substring(original.lastIndexOf('.')).toLowerCase();
            }
            tempFile = File.createTempFile("vibe-audio-", suffix);
            audio.transferTo(tempFile);

            // 优先 mp3agic，性能更好
            if (suffix.endsWith(".mp3")) {
                Mp3File mp3File = new Mp3File(tempFile);
                long seconds = mp3File.getLengthInSeconds();
                return String.valueOf(seconds);
            }

            // 其它格式（含 .flac）尝试用 jaudiotagger（通过反射以避免强依赖导致编译失败）
            try {
                Class<?> audioFileIO = Class.forName("org.jaudiotagger.audio.AudioFileIO");
                Object af = audioFileIO.getMethod("read", File.class).invoke(null, tempFile);
                Object header = af.getClass().getMethod("getAudioHeader").invoke(af);
                Object len = header.getClass().getMethod("getTrackLength").invoke(header);
                int seconds = (len instanceof Integer) ? (Integer) len : Integer.parseInt(String.valueOf(len));
                if (seconds > 0) return String.valueOf(seconds);
            } catch (Throwable ignore) {
                // ignore and return null
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                // noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }
}




