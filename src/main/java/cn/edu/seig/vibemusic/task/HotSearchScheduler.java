package cn.edu.seig.vibemusic.task;

import cn.edu.seig.vibemusic.service.HotSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热搜榜定时任务：每日 0 点清空
 */
@Component
public class HotSearchScheduler {

    private static final Logger log = LoggerFactory.getLogger(HotSearchScheduler.class);

    @Autowired
    private HotSearchService hotSearchService;

    /**
     * 每日 0 点执行（服务器时区）
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void clearDailyHotSearch() {
        try {
            hotSearchService.clearAll();
            log.info("[HotSearch] Daily clear executed.");
        } catch (Exception e) {
            log.error("[HotSearch] Daily clear failed.", e);
        }
    }
}


