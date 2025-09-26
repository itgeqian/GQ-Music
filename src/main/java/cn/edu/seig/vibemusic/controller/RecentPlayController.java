package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IRecentPlayService;
import cn.edu.seig.vibemusic.util.ThreadLocalUtil;
import cn.edu.seig.vibemusic.util.TypeConversionUtil;
import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/recent")
@RequiredArgsConstructor
public class RecentPlayController {

    private final IRecentPlayService recentPlayService;

    private Long currentUserId() {
        try {
            java.util.Map<String, Object> map = ThreadLocalUtil.get();
            return TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
        } catch (Exception ignored) {}
        return null;
    }

    @PostMapping("/play")
    public Result<?> report(@RequestBody java.util.Map<String, Object> body, HttpServletRequest req) {
        Long userId = currentUserId();
        if (userId == null) return Result.error("未登录");
        Object sid = body.get("songId");
        Long songId = sid == null ? null : Long.valueOf(String.valueOf(sid));
        recentPlayService.reportRecent(userId, songId);
        return Result.success();
    }

    @PostMapping("/list")
    public Result<PageResult<?>> list(@RequestBody java.util.Map<String, Object> body, HttpServletRequest req) {
        Long userId = currentUserId();
        if (userId == null) return Result.error("未登录");
        Integer pageNum = body.get("pageNum") == null ? 1 : Integer.valueOf(String.valueOf(body.get("pageNum")));
        Integer pageSize = body.get("pageSize") == null ? 20 : Integer.valueOf(String.valueOf(body.get("pageSize")));
        PageResult<?> pr = recentPlayService.page(userId, pageNum, pageSize);
        return Result.success(pr);
    }

    /** 按用户ID查询（用于查看他人主页的榜单） */
    @PostMapping("/listByUser")
    public Result<PageResult<?>> listByUser(@RequestBody java.util.Map<String, Object> body) {
        Object uid = body.get("userId");
        if (uid == null) return Result.error("缺少userId");
        Long userId = Long.valueOf(String.valueOf(uid));
        Integer pageNum = body.get("pageNum") == null ? 1 : Integer.valueOf(String.valueOf(body.get("pageNum")));
        Integer pageSize = body.get("pageSize") == null ? 20 : Integer.valueOf(String.valueOf(body.get("pageSize")));
        PageResult<?> pr = recentPlayService.page(userId, pageNum, pageSize);
        return Result.success(pr);
    }

    /** 删除最近播放中的一条 */
    @DeleteMapping("/one")
    public Result<?> removeOne(@RequestParam("songId") Long songId) {
        Long userId = currentUserId();
        if (userId == null) return Result.error("未登录");
        recentPlayService.removeOne(userId, songId);
        return Result.success();
    }

    /** 清空当前用户最近播放 */
    @DeleteMapping("/clear")
    public Result<?> clearAll() {
        Long userId = currentUserId();
        if (userId == null) return Result.error("未登录");
        recentPlayService.clearAll(userId);
        return Result.success();
    }
}


