package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.entity.Theme;
import cn.edu.seig.vibemusic.model.entity.UserTheme;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IThemeService extends IService<Theme> {

    Result<List<Theme>> listOfficialThemes();

    Result<UserTheme> getMyTheme();

    Result<Void> setMyThemeById(Long themeId);

    Result<String> uploadCustomBackground(org.springframework.web.multipart.MultipartFile file);

    Result<Void> setMyCustomBackground(String imageUrl, String blurhash, String colorPrimary);

    Result<Void> resetMyTheme();

    // ============== 管理端 ==============
    Result<Void> adminAddTheme(org.springframework.web.multipart.MultipartFile file, String name);

    Result<Void> adminUpdateStatus(Long themeId, Integer status);

    Result<Void> adminDelete(Long themeId);
}


