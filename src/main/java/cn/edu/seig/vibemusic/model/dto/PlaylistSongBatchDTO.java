package cn.edu.seig.vibemusic.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PlaylistSongBatchDTO {

    @NotNull
    private Long playlistId;

    @NotNull
    private List<Long> songIds;
}


