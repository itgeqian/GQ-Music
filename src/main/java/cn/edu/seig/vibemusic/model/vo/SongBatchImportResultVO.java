package cn.edu.seig.vibemusic.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量导入歌曲结果VO
 *
 * @author geqian
 * @since 2025-01-09
 */
@Data
public class SongBatchImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 总数量
     */
    private Integer totalCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failureCount;

    /**
     * 失败详情
     */
    private List<ImportFailureItem> failures;

    @Data
    public static class ImportFailureItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 行号（从1开始）
         */
        private Integer rowNumber;

        /**
         * 歌名
         */
        private String songName;

        /**
         * 失败原因
         */
        private String reason;
    }
}
