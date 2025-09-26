package cn.edu.seig.vibemusic.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 官方主题实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_theme")
public class Theme implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long themeId;

    @TableField("name")
    private String name;

    /** 1080p 背景图 */
    @TableField("url_1080")
    private String url1080;

    /** 1440p 背景图（可选） */
    @TableField("url_1440")
    private String url1440;

    /** 缩略图 */
    @TableField("thumb_url")
    private String thumbUrl;

    /** 0 图片 1 视频 */
    @TableField("type")
    private Integer type;

    /** 视频地址 */
    @TableField("video_url")
    private String videoUrl;

    /** 视频海报（首帧） */
    @TableField("poster_url")
    private String posterUrl;

    /** 时长（秒，可选） */
    @TableField("duration")
    private Integer duration;

    @TableField("blurhash")
    private String blurhash;

    /** 是否需要会员：0-否 1-是 */
    @TableField("need_vip")
    private Integer needVip;

    /** 状态：1-上架 0-下架 */
    @TableField("status")
    private Integer status;

    /** 排序（越大越靠前） */
    @TableField("sort")
    private Integer sort;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}


