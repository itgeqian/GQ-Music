package cn.edu.seig.vibemusic.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tb_album")
public class Album implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long albumId;

    @TableField("artist_id")
    private Long artistId;

    @TableField("title")
    private String title;

    @TableField("cover_url")
    private String coverUrl;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("release_date")
    private LocalDate releaseDate;

    @TableField("category")
    private String category;

    @TableField("introduction")
    private String introduction;

    @TableField("details")
    private String details;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}


