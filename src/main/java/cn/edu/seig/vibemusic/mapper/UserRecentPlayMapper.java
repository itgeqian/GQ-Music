package cn.edu.seig.vibemusic.mapper;

import cn.edu.seig.vibemusic.model.entity.UserRecentPlay;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRecentPlayMapper extends BaseMapper<UserRecentPlay> {

    @Select("SELECT COUNT(1) FROM tb_user_recent_play WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    @Select("SELECT id FROM tb_user_recent_play WHERE user_id = #{userId} ORDER BY create_time ASC LIMIT #{limit}")
    List<Long> findOldestIds(@Param("userId") Long userId, @Param("limit") int limit);
}


