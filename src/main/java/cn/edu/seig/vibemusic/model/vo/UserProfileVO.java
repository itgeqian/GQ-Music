package cn.edu.seig.vibemusic.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserProfileVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String userAvatar;
    private String introduction;

    /** 粉丝数 */
    private long fans;
    /** 关注数 */
    private long followings;
    /** 我是否已关注该用户（仅登录时） */
    private Boolean followedByMe;

    /** 是否为私密用户 */
    private Boolean privateUser;
}


