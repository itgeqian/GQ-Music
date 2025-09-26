package cn.edu.seig.vibemusic.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CommentSongDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 歌曲id
     */
    private Long songId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 图片路径
     */
    private String imgPath;

    /**
     * 回复的评论ID（可选，表示二级回复）
     */
    private Long replyCommentId;

}
