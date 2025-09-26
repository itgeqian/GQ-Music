package cn.edu.seig.vibemusic.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AlbumDTO implements Serializable {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 10;

    /** 歌手ID（可选） */
    private Long artistId;

    /** 专辑标题（模糊查询，可选） */
    private String title;
}


