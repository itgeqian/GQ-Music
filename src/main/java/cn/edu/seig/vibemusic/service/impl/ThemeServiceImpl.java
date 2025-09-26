package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.mapper.ThemeMapper;
import cn.edu.seig.vibemusic.mapper.UserThemeMapper;
import cn.edu.seig.vibemusic.model.entity.Theme;
import cn.edu.seig.vibemusic.model.entity.UserTheme;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IThemeService;
import cn.edu.seig.vibemusic.service.MinioService;
import cn.edu.seig.vibemusic.util.ThreadLocalUtil;
import cn.edu.seig.vibemusic.util.TypeConversionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;

import cn.edu.seig.vibemusic.util.FFmpegUtils;

@Service
public class ThemeServiceImpl extends ServiceImpl<ThemeMapper, Theme> implements IThemeService {

    @Autowired
    private ThemeMapper themeMapper;
    @Autowired
    private UserThemeMapper userThemeMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private FFmpegUtils ffmpegUtils;

    @Override
    public Result<List<Theme>> listOfficialThemes() {
        List<Theme> list = themeMapper.selectList(new QueryWrapper<Theme>().eq("status", 1).orderByDesc("sort"));
        return Result.success(list);
    }

    private Long currentUserId() {
        java.util.Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        return TypeConversionUtil.toLong(userIdObj);
    }

    @Override
    public Result<UserTheme> getMyTheme() {
        Long userId = currentUserId();
        UserTheme ut = userThemeMapper.selectById(userId);
        return Result.success(ut);
    }

    @Override
    public Result<Void> setMyThemeById(Long themeId) {
        Long userId = currentUserId();
        UserTheme ut = new UserTheme()
                .setUserId(userId)
                .setThemeType("official")
                .setThemeId(themeId)
                .setImageUrl(null)
                .setUpdatedAt(LocalDateTime.now());
        // upsert
        if (userThemeMapper.selectById(userId) == null) {
            userThemeMapper.insert(ut);
        } else {
            userThemeMapper.updateById(ut);
        }
        return Result.success();
    }

    @Override
    public Result<String> uploadCustomBackground(MultipartFile file) {
        String url = minioService.uploadFile(file, "user-themes");
        // 使用带 data 的 success 重载，避免把 URL 放到 message 字段
        return Result.success("上传成功", url);
    }

    @Override
    public Result<Void> setMyCustomBackground(String imageUrl, String blurhash, String colorPrimary) {
        Long userId = currentUserId();
        UserTheme ut = new UserTheme()
                .setUserId(userId)
                .setThemeType("custom")
                .setThemeId(null)
                .setImageUrl(imageUrl)
                .setBlurhash(blurhash)
                .setColorPrimary(colorPrimary)
                .setUpdatedAt(LocalDateTime.now());
        if (userThemeMapper.selectById(userId) == null) {
            userThemeMapper.insert(ut);
        } else {
            userThemeMapper.updateById(ut);
        }
        return Result.success();
    }

    @Override
    public Result<Void> resetMyTheme() {
        Long userId = currentUserId();
        userThemeMapper.deleteById(userId);
        return Result.success();
    }

    // ================= 管理端 =================
    @Override
    public Result<Void> adminAddTheme(MultipartFile file, String name) {
        String objectUrl = minioService.uploadFile(file, "themes");
        Theme theme = new Theme();
        String themeName = (name != null && !name.isBlank())
                ? name
                : (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                    ? file.getOriginalFilename()
                    : "主题壁纸");
        theme.setName(themeName);
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (contentType.startsWith("video/")) {
            // 视频主题：生成首帧 poster 与时长
            theme.setType(1);
            theme.setVideoUrl(objectUrl);
            try {
                // 将上传的文件保存到本地临时，再用 ffmpeg 处理（避免通过 URL 下载失败）
                File tmp = Files.createTempFile("theme-video-", ".mp4").toFile();
                try (var in = file.getInputStream()) {
                    Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                // 时长
                int duration = ffmpegUtils.getDurationSeconds(tmp.getAbsolutePath());
                theme.setDuration(duration);
                // 海报
                File poster = Files.createTempFile("theme-poster-", ".jpg").toFile();
                ffmpegUtils.createVideoFirstFrame(tmp.getAbsolutePath(), poster.getAbsolutePath(), 320);
                try (FileInputStream fis = new FileInputStream(poster)) {
                    String posterUrl = minioService.uploadStream(fis, "poster.jpg", "image/jpeg", "themes", poster.length());
                    theme.setPosterUrl(posterUrl);
                }
                poster.delete();
                tmp.delete();
            } catch (Exception ignored) {}
        } else {
            // 图片主题
            theme.setType(0);
            theme.setUrl1080(objectUrl);
            theme.setThumbUrl(objectUrl);
        }
        theme.setStatus(1);
        theme.setSort(0);
        theme.setCreatedAt(LocalDateTime.now());
        theme.setUpdatedAt(LocalDateTime.now());
        themeMapper.insert(theme);
        return Result.success();
    }

    @Override
    public Result<Void> adminUpdateStatus(Long themeId, Integer status) {
        Theme theme = new Theme();
        theme.setThemeId(themeId);
        theme.setStatus(status);
        theme.setUpdatedAt(LocalDateTime.now());
        themeMapper.updateById(theme);
        return Result.success();
    }

    @Override
    public Result<Void> adminDelete(Long themeId) {
        Theme old = themeMapper.selectById(themeId);
        if (old != null) {
            // 可选：删除对象存储中的文件（忽略失败）
            try { if (old.getUrl1080() != null) minioService.deleteFile(old.getUrl1080()); } catch (Exception ignored) {}
            try { if (old.getUrl1440() != null) minioService.deleteFile(old.getUrl1440()); } catch (Exception ignored) {}
            try { if (old.getThumbUrl() != null) minioService.deleteFile(old.getThumbUrl()); } catch (Exception ignored) {}
            try { if (old.getVideoUrl() != null) minioService.deleteFile(old.getVideoUrl()); } catch (Exception ignored) {}
            try { if (old.getPosterUrl() != null) minioService.deleteFile(old.getPosterUrl()); } catch (Exception ignored) {}
        }
        themeMapper.deleteById(themeId);
        return Result.success();
    }
}


