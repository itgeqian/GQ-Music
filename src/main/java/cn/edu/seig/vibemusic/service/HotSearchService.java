package cn.edu.seig.vibemusic.service;

import java.util.List;

/**
 * 热搜服务
 */
public interface HotSearchService {

    /**
     * 关键字计数 +1
     */
    void increaseKeyword(String keyword);

    /**
     * 读取 TopN 关键字（倒序）
     */
    List<String> getTopKeywords(int topN);

    /**
     * 清空热搜榜数据
     */
    void clearAll();
}


