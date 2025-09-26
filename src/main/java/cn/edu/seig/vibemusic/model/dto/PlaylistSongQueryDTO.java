package cn.edu.seig.vibemusic.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaylistSongQueryDTO {
    @NotNull
    private Long playlistId;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword; // 歌曲名/歌手名 模糊
}


