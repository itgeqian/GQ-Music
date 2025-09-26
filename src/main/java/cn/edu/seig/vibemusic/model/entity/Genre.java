package cn.edu.seig.vibemusic.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_genre")
public class Genre implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 歌曲 id */
    @TableId(value = "song_id", type = IdType.INPUT)
    private Long songId;

    /** 专辑 id（所属专辑） */
    @TableField("album_id")
    private Long albumId;

    /** 歌曲风格 id（tb_style.id） */
    @TableField("style_id")
    private Long styleId;

    /** 专辑类型 id（tb_style.id） */
    @TableField("album_style_id")
    private Long albumStyleId;

}
