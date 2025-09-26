package cn.edu.seig.vibemusic.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class CommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评论 id
     */
    private Long commentId;

    /**
     * 用户 id（用于前端跳转个人详情）
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTime;

    /**
     * 点赞数量
     */
    private Long likeCount;

    /**
     * 图片路径
     */
    private String imgPath;

    /** 父级评论ID；0 表示一级 */
    private Long pCommentId;

    /** 被回复的用户ID（二级回复时） */
    private Long replyUserId;

    /** 被回复的用户昵称（@对象） */
    private String replyNickName;

    /** 子级回复列表（仅二级） */
    private java.util.List<CommentVO> children;

}
