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
 * @author  geqian
 * @since 2025-01-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_comment")
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评论 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long commentId;

    /**
     * 用户 id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 歌曲 id
     */
    @TableField("song_id")
    private Long songId;

    /**
     * 歌单 id
     */
    @TableField("playlist_id")
    private Long playlistId;

    /**
     * 专辑 id（用于专辑评论）
     */
    @TableField("album_id")
    private Long albumId;

    /**
     * 评论内容
     */
    @TableField("content")
    private String content;

    /**
     * 评论时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 评论类型：0-歌曲评论，1-歌单评论
     */
    @TableField("type")
    private Integer type;

    /**
     * 点赞数量
     */
    @TableField("like_count")
    private Long likeCount;

    /**
     * 图片路径
     */
    @TableField("img_path")
    private String imgPath;

    /**
     * 父级评论ID；0 表示一级
     */
    @TableField("p_comment_id")
    private Long pCommentId;

    /**
     * 被回复的用户ID（二级回复时）
     */
    @TableField("reply_user_id")
    private Long replyUserId;

    /**
     * 是否置顶（仅一级生效）：0 否，1 是
     */
    @TableField("top_type")
    private Integer topType;

}
