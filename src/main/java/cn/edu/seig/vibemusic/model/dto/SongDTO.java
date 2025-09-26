package cn.edu.seig.vibemusic.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SongDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    @NotNull
    private Integer pageNum;

    /**
     * 每页数量
     */
    @NotNull
    private Integer pageSize;

    /**
     * 统一关键字（歌名/歌手/专辑），优先使用
     */
    private String keyword;

    /**
     * 歌曲名
     */
    private String songName;

    /**
     * 歌手
     */
    private String artistName;

    /**
     * 专辑
     */
    private String album;

}
