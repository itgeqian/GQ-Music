package cn.edu.seig.vibemusic.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * <p>
 * 
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_song")
public class Song implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 歌曲 id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long songId;

    /**
     * 歌手 id
     */
    @TableField("artist_id")
    private Long artistId;

    /**
     * 歌名
     */
    @TableField("name")
    private String songName;

    /**
     * 专辑
     */
    @TableField("album")
    private String album;

    /**
     * 专辑ID（规范关联）
     */
    @TableField("album_id")
    private Long albumId;

    /**
     * 旧版内嵌歌词（已废弃，数据库已去除该列）
     */
    @TableField(exist = false)
    private String lyric;

    /**
     * 歌词文件 URL（MinIO）
     */
    @TableField("lyric_url")
    private String lyricUrl;

    /**
     * 歌曲时长
     */
    @TableField("duration")
    private String duration;

    /**
     * 歌曲风格
     */
    @TableField("style")
    private String style;

    /**
     * 歌曲封面 url
     */
    @TableField("cover_url")
    private String coverUrl;

    /**
     * 歌曲 url
     */
    @TableField("audio_url")
    private String audioUrl;

    /**
     * 歌曲发行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("release_time")
    private LocalDate releaseTime;

}
