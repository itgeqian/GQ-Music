package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.HotSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索相关开放接口
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private HotSearchService hotSearchService;

    /**
     * 获取热搜 TopN（默认10）
     */
    @GetMapping("/getHotKeywords")
    public Result<List<String>> getHotKeywords(@RequestParam(required = false, defaultValue = "10") Integer top) {
        return Result.success(hotSearchService.getTopKeywords(top));
    }

    /**
     * 主动上报一次关键字计数（前端在重复搜索但不刷新列表时可调用）
     */
    @RequestMapping("/reportKeyword")
    public Result<String> reportKeyword(@RequestParam String keyword) {
        hotSearchService.increaseKeyword(keyword);
        return Result.success("OK");
    }
}


