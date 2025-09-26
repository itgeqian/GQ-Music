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
 * 用户主题选择/自定义
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_theme")
public class UserTheme implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** user_id 作为主键，1:1 存储 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** official/custom */
    @TableField("theme_type")
    private String themeType;

    /** 选择官方主题时的ID */
    @TableField("theme_id")
    private Long themeId;

    /** 自定义背景URL */
    @TableField("image_url")
    private String imageUrl;

    @TableField("blurhash")
    private String blurhash;

    @TableField("color_primary")
    private String colorPrimary;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}


