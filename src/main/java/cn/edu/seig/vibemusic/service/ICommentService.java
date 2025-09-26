package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.CommentPlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.CommentAlbumDTO;
import cn.edu.seig.vibemusic.model.dto.CommentSongDTO;
import cn.edu.seig.vibemusic.model.entity.Comment;
import cn.edu.seig.vibemusic.model.vo.CommentVO;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
public interface ICommentService extends IService<Comment> {

    // 新增歌曲评论
    Result<String> addSongComment(CommentSongDTO commentSongDTO);

    // 新增歌单评论
    Result<String> addPlaylistComment(CommentPlaylistDTO commentPlaylistDTO);

    // 新增专辑评论
    Result<String> addAlbumComment(CommentAlbumDTO commentAlbumDTO);

    // 获取专辑评论列表
    Result<java.util.List<CommentVO>> getAlbumComments(Long albumId);

    // 获取歌曲评论列表
    Result<java.util.List<CommentVO>> getSongComments(Long songId);

    // 获取歌单评论列表
    Result<java.util.List<CommentVO>> getPlaylistComments(Long playlistId);

    // 点赞评论
    Result<String> likeComment(Long commentId);

    // 取消点赞评论
    Result<String> cancelLikeComment(Long commentId);

    // 删除评论
    Result<String> deleteComment(Long commentId);

}
