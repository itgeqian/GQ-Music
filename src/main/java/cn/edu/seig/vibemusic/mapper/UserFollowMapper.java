package cn.edu.seig.vibemusic.mapper;

import cn.edu.seig.vibemusic.model.entity.UserFollow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT COUNT(1) FROM tb_user_follow WHERE followee_id = #{userId}")
    long countFans(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM tb_user_follow WHERE follower_id = #{userId}")
    long countFollowings(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM tb_user_follow WHERE follower_id = #{follower} AND followee_id = #{followee}")
    long exists(@Param("follower") Long follower, @Param("followee") Long followee);

    // 查询我关注的 userId 列表
    @Select("SELECT followee_id FROM tb_user_follow WHERE follower_id = #{userId}")
    List<Long> findFolloweeIds(@Param("userId") Long userId);

    // 查询关注我的 userId 列表
    @Select("SELECT follower_id FROM tb_user_follow WHERE followee_id = #{userId}")
    List<Long> findFollowerIds(@Param("userId") Long userId);
}


