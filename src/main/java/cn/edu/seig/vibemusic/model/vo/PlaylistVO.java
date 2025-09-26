package cn.edu.seig.vibemusic.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PlaylistVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 歌单 id
     */
    private Long playlistId;

    /**
     * 歌单标题
     */
    private String title;

    /**
     * 歌单封面
     */
    private String coverUrl;

    /**
     * 歌单风格
     */
    private String style;

    /**
     * 歌单简介
     */
    private String introduction;

}
