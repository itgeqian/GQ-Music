package cn.edu.seig.vibemusic.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentAlbumDTO {

    /** 专辑ID */
    @NotNull
    private Long albumId;

    /** 评论内容 */
    @NotBlank
    private String content;

    /** 图片路径 */
    private String imgPath;

    /** 回复的评论ID（可选） */
    private Long replyCommentId;
}


