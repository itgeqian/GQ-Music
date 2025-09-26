package cn.edu.seig.vibemusic.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CommentPlaylistDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 歌单id
     */
    private Long playlistId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 图片路径
     */
    private String imgPath;

    /** 回复的评论ID（可选） */
    private Long replyCommentId;

}
