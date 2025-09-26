package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.model.entity.Theme;
import cn.edu.seig.vibemusic.model.entity.UserTheme;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IThemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping
public class ThemeController {

    @Autowired
    private IThemeService themeService;

    /** 用户端：获取官方主题列表 */
    @GetMapping("/theme/list")
    public Result<List<Theme>> listThemes() {
        return themeService.listOfficialThemes();
    }

    /** 用户端：获取当前用户主题 */
    @GetMapping("/user/theme")
    public Result<UserTheme> getMyTheme() {
        return themeService.getMyTheme();
    }

    /** 用户端：设置官方主题 */
    @PutMapping("/user/theme")
    public Result<Void> setMyTheme(@RequestParam("themeId") Long themeId) {
        return themeService.setMyThemeById(themeId);
    }

    /** 用户端：上传自定义背景，返回URL */
    @PostMapping("/user/theme/upload")
    public Result<String> uploadCustom(@RequestParam("file") MultipartFile file) {
        return themeService.uploadCustomBackground(file);
    }

    /** 用户端：设置自定义背景 */
    @PostMapping("/user/theme/custom")
    public Result<Void> setCustom(@RequestParam("imageUrl") String imageUrl,
                                  @RequestParam(value = "blurhash", required = false) String blurhash,
                                  @RequestParam(value = "colorPrimary", required = false) String colorPrimary) {
        return themeService.setMyCustomBackground(imageUrl, blurhash, colorPrimary);
    }

    /** 用户端：恢复默认（删除记录） */
    @DeleteMapping("/user/theme")
    public Result<Void> reset() {
        return themeService.resetMyTheme();
    }

    // ================= 管理端 =================
    @PostMapping("/admin/theme/add")
    public Result<Void> adminAdd(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "name", required = false) String name) {
        return themeService.adminAddTheme(file, name);
    }

    @PatchMapping("/admin/theme/status/{id}")
    public Result<Void> adminStatus(@PathVariable("id") Long id,
                                    @RequestParam("status") Integer status) {
        return themeService.adminUpdateStatus(id, status);
    }

    @DeleteMapping("/admin/theme/{id}")
    public Result<Void> adminDelete(@PathVariable("id") Long id) {
        return themeService.adminDelete(id);
    }

    /** 管理端：主题列表（含名称/排序/状态/VIP） */
    @GetMapping("/admin/theme/list")
    public Result<java.util.List<Theme>> adminList() {
        return themeService.listOfficialThemes();
    }

    /** 管理端：更新主题元信息（名称、排序、VIP） */
    @PatchMapping("/admin/theme/update/{id}")
    public Result<Void> adminUpdate(@PathVariable("id") Long id,
                                    @RequestParam(value = "name", required = false) String name,
                                    @RequestParam(value = "sort", required = false) Integer sort,
                                    @RequestParam(value = "needVip", required = false) Integer needVip) {
        Theme t = new Theme();
        t.setThemeId(id);
        if (name != null) t.setName(name);
        if (sort != null) t.setSort(sort);
        if (needVip != null) t.setNeedVip(needVip);
        // 直接复用 service 的更新
        themeService.updateById(t);
        return Result.success();
    }
}


