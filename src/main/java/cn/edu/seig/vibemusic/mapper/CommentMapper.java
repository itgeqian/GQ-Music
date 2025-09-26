package cn.edu.seig.vibemusic.mapper;

import cn.edu.seig.vibemusic.model.entity.Comment;
import cn.edu.seig.vibemusic.model.vo.CommentVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.ResultMap;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    /** 点赞 +1，原子更新 */
    @Update("UPDATE tb_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incLikeCount(Long commentId);

    /** 取消点赞 -1，最小为 0，原子更新 */
    @Update("UPDATE tb_comment SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END WHERE id = #{commentId}")
    int decLikeCount(Long commentId);

    /** 按专辑获取评论列表（type=2），包含用户名与头像 */
    @Select("SELECT c.id AS commentId, u.id AS userId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount, c.img_path AS imgPath FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.album_id = #{albumId} AND c.type = 2 ORDER BY c.id DESC")
    List<CommentVO> getAlbumComments(Long albumId);

    /** 按歌曲获取评论列表（type=0） */
    @Select("SELECT c.id AS commentId, u.id AS userId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount, c.img_path AS imgPath FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.song_id = #{songId} AND c.type = 0 ORDER BY c.id DESC")
    List<CommentVO> getSongComments(Long songId);

    /** 按歌单获取评论列表（type=1） */
    @Select("SELECT c.id AS commentId, u.id AS userId, u.username AS username, u.user_avatar AS userAvatar, c.content AS content, c.create_time AS createTime, c.like_count AS likeCount, c.img_path AS imgPath FROM tb_comment c LEFT JOIN tb_user u ON c.user_id = u.id WHERE c.playlist_id = #{playlistId} AND c.type = 1 ORDER BY c.id DESC")
    List<CommentVO> getPlaylistComments(Long playlistId);

    // 带子级的查询由 XML 提供：selectChildComments

    // ========== XML 映射的树形查询（方法签名用于绑定 XML 的 <select id=...>） ==========
    List<CommentVO> getAlbumCommentsWithChildren(Long albumId);
    List<CommentVO> getSongCommentsWithChildren(Long songId);
    List<CommentVO> getPlaylistCommentsWithChildren(Long playlistId);
}
