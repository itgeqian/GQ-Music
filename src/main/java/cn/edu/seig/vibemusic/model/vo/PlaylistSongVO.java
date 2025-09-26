package cn.edu.seig.vibemusic.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Data
public class PlaylistSongVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定记录主键 */
    private Long id;


    /** 歌曲信息 */
    private Long songId;
    private String songName;
    private String artistName;
    private String album;
    private String coverUrl;
    private String audioUrl;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseTime;
}


