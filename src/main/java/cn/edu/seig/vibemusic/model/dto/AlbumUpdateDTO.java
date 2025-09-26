package cn.edu.seig.vibemusic.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class AlbumUpdateDTO implements Serializable {
    @NotNull
    private Long albumId;
    private Long artistId;
    private String title;
    private String coverUrl;
    private String releaseDate;
    private String category;
    private String introduction;
    private String details;
}


