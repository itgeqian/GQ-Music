package cn.edu.seig.vibemusic.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum FavoriteTypeEnum {

    SONG(0, "歌曲收藏"),
    PLAYLIST(1, "歌单收藏"),
    ARTIST(2, "关注歌手"),
    ALBUM(3, "专辑收藏");

    @EnumValue
    private final Integer id;
    private final String favoriteType;

    FavoriteTypeEnum(Integer id, String favoriteType) {
        this.id = id;
        this.favoriteType = favoriteType;
    }

}
