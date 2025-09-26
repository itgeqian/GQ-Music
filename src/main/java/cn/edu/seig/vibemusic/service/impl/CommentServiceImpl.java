package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.enumeration.RoleEnum;
import cn.edu.seig.vibemusic.mapper.CommentMapper;
import cn.edu.seig.vibemusic.model.dto.CommentPlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.CommentAlbumDTO;
import cn.edu.seig.vibemusic.model.dto.CommentSongDTO;
import cn.edu.seig.vibemusic.model.entity.Comment;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.model.vo.CommentVO;
import cn.edu.seig.vibemusic.service.ICommentService;
import cn.edu.seig.vibemusic.util.ThreadLocalUtil;
import cn.edu.seig.vibemusic.util.TypeConversionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    @Autowired
    private CommentMapper commentMapper;

    /**
     * 添加歌曲评论
     *
     * @param commentSongDTO 歌曲评论DTO
     * @return Result
     */
    @Override
    @CacheEvict(cacheNames = "songCache", allEntries = true)
    public Result<String> addSongComment(CommentSongDTO commentSongDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        Comment comment = new Comment();
        Long replyId = commentSongDTO.getReplyCommentId();
        Long pId = 0L;
        Long replyUserId = null;
        if (replyId != null) {
            Comment parent = commentMapper.selectById(replyId);
            if (parent != null) {
                pId = parent.getPCommentId() == null || parent.getPCommentId() == 0 ? parent.getCommentId() : parent.getPCommentId();
                // 无论回复的是一级还是二级，都记录被@的用户，便于前端展示“@xxx”
                replyUserId = parent.getUserId();
            }
        }
        comment.setUserId(userId)
                .setSongId(commentSongDTO.getSongId())
                .setPlaylistId(null)
                .setAlbumId(null)
                .setContent(commentSongDTO.getContent())
                .setImgPath(commentSongDTO.getImgPath())
                .setType(0)
                .setPCommentId(pId)
                .setReplyUserId(replyUserId)
                .setTopType(0)
                .setCreateTime(LocalDateTime.now()).setLikeCount(0L);

        if (commentMapper.insert(comment) == 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 添加歌单评论
     *
     * @param commentPlaylistDTO 歌单评论DTO
     * @return Result
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> addPlaylistComment(CommentPlaylistDTO commentPlaylistDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        Comment comment = new Comment();
        Long replyId = commentPlaylistDTO.getReplyCommentId();
        Long pId = 0L;
        Long replyUserId = null;
        if (replyId != null) {
            Comment parent = commentMapper.selectById(replyId);
            if (parent != null) {
                pId = parent.getPCommentId() == null || parent.getPCommentId() == 0 ? parent.getCommentId() : parent.getPCommentId();
                replyUserId = parent.getUserId();
            }
        }
        comment.setUserId(userId)
                .setPlaylistId(commentPlaylistDTO.getPlaylistId())
                .setAlbumId(null)
                .setContent(commentPlaylistDTO.getContent())
                .setImgPath(commentPlaylistDTO.getImgPath())
                .setType(1)
                .setPCommentId(pId)
                .setReplyUserId(replyUserId)
                .setTopType(0)
                .setCreateTime(LocalDateTime.now()).setLikeCount(0L);

        if (commentMapper.insert(comment) == 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 添加专辑评论
     */
    @Override
    @CacheEvict(cacheNames = "albumCache", allEntries = true)
    public Result<String> addAlbumComment(CommentAlbumDTO commentAlbumDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        Comment comment = new Comment();
        Long replyId = commentAlbumDTO.getReplyCommentId();
        Long pId = 0L;
        Long replyUser = null;
        if (replyId != null) {
            Comment parent = commentMapper.selectById(replyId);
            if (parent != null) {
                pId = parent.getPCommentId() == null || parent.getPCommentId() == 0 ? parent.getCommentId() : parent.getPCommentId();
                replyUser = parent.getUserId();
            }
        }
        comment.setUserId(userId)
                .setAlbumId(commentAlbumDTO.getAlbumId())
                .setPlaylistId(null)
                .setContent(commentAlbumDTO.getContent())
                .setImgPath(commentAlbumDTO.getImgPath())
                .setType(2)
                .setPCommentId(pId)
                .setReplyUserId(replyUser)
                .setTopType(0)
                .setCreateTime(LocalDateTime.now())
                .setLikeCount(0L);

        if (commentMapper.insert(comment) == 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /** 获取专辑评论列表 */
    @Override
    public Result<java.util.List<CommentVO>> getAlbumComments(Long albumId) {
        java.util.List<CommentVO> list = commentMapper.getAlbumCommentsWithChildren(albumId);
        return Result.success(list);
    }

    /** 获取歌曲评论列表 */
    @Override
    public Result<java.util.List<CommentVO>> getSongComments(Long songId) {
        java.util.List<CommentVO> list = commentMapper.getSongCommentsWithChildren(songId);
        return Result.success(list);
    }

    /** 获取歌单评论列表 */
    @Override
    public Result<java.util.List<CommentVO>> getPlaylistComments(Long playlistId) {
        java.util.List<CommentVO> list = commentMapper.getPlaylistCommentsWithChildren(playlistId);
        return Result.success(list);
    }

    /**
     * 点赞评论
     *
     * @param commentId 评论ID
     * @return Result
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
    public Result<String> likeComment(Long commentId) {
        int updated = commentMapper.incLikeCount(commentId);
        if (updated == 0) {
            return Result.error(MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.SUCCESS);
    }

    /**
     * 取消点赞评论
     *
     * @param commentId 评论ID
     * @return Result
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "playlistCache"}, allEntries = true)
    public Result<String> cancelLikeComment(Long commentId) {
        int updated = commentMapper.decLikeCount(commentId);
        if (updated == 0) {
            return Result.error(MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.SUCCESS);
    }

    /**
     * 删除评论
     *
     * @param commentId 评论ID
     * @return Result
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "playlistCache", "albumCache"}, allEntries = true)
    public Result<String> deleteComment(Long commentId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String role = map == null ? null : (String) map.get(JwtClaimsConstant.ROLE);
        Long userId = null;
        boolean isAdmin = RoleEnum.ADMIN.getRole().equals(role);
        if (!isAdmin) {
            Object userIdObj = map == null ? null : map.get(JwtClaimsConstant.USER_ID);
            if (userIdObj == null) {
                return Result.error(MessageConstant.NO_PERMISSION);
            }
            userId = TypeConversionUtil.toLong(userIdObj);
        }

        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return Result.error(MessageConstant.NOT_FOUND);
        }
        if (!isAdmin && !Objects.equals(comment.getUserId(), userId)) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }

        // 若为父级评论，连同其子级一并删除
        if (comment.getPCommentId() == null || comment.getPCommentId() == 0) {
            // 删除子级
            commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Comment>()
                    .eq("p_comment_id", commentId));
        }

        if (commentMapper.deleteById(commentId) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }
}
