package cn.edu.seig.vibemusic.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class AlbumVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long albumId;
    private Long artistId;
    private String artistName;
    private String title;
    private String coverUrl;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseDate;
    private String category;
    private String introduction;
    private String details;
}


