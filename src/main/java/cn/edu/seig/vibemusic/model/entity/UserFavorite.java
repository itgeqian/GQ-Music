package cn.edu.seig.vibemusic.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serial;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
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
@TableName("tb_user_favorite")
public class UserFavorite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long userFavoriteId;

    /**
     * 用户 id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 收藏类型：0-歌曲，1-歌单
     */
    @TableField("type")
    private Integer type;

    /**
     * 收藏歌曲 id
     */
    @TableField("song_id")
    private Long songId;

    /**
     * 收藏歌单 id
     */
    @TableField("playlist_id")
    private Long playlistId;

    /**
     * 关注歌手 id（当 type = 2 时使用）
     */
    @TableField("artist_id")
    private Long artistId;

    /**
     * 收藏专辑 id（当 type = 3 时使用）
     */
    @TableField("album_id")
    private Long albumId;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

}
