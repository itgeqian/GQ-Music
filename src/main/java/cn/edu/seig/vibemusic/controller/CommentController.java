package cn.edu.seig.vibemusic.controller;


import cn.edu.seig.vibemusic.model.dto.CommentPlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.CommentAlbumDTO;
import cn.edu.seig.vibemusic.model.dto.CommentSongDTO;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.model.vo.CommentVO;
import cn.edu.seig.vibemusic.service.ICommentService;
import cn.edu.seig.vibemusic.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private ICommentService commentService;

    @Autowired
    private MinioService minioService;

    /**
     * 新增歌曲评论
     *
     * @param commentSongDTO 评论信息
     * @return 结果
     */
    @PostMapping("/addSongComment")
    public Result<String> addSongComment(@RequestBody CommentSongDTO commentSongDTO) {
        return commentService.addSongComment(commentSongDTO);
    }

    /**
     * 新增歌单评论
     *
     * @param commentPlaylistDTO 评论信息
     * @return 结果
     */
    @PostMapping("/addPlaylistComment")
    public Result<String> addPlaylistComment(@RequestBody CommentPlaylistDTO commentPlaylistDTO) {
        return commentService.addPlaylistComment(commentPlaylistDTO);
    }

    /**
     * 点赞评论
     *
     * @param commentId 评论id
     * @return 结果
     */
    @PatchMapping("/likeComment/{id}")
    public Result<String> likeComment(@PathVariable("id") Long commentId) {
        return commentService.likeComment(commentId);
    }

    /**
     * 取消点赞评论
     *
     * @param commentId 评论id
     * @return 结果
     */
    @PatchMapping("/cancelLikeComment/{id}")
    public Result<String> cancelLikeComment(@PathVariable("id") Long commentId) {
        return commentService.cancelLikeComment(commentId);
    }

    /**
     * 上传评论图片
     *
     * @param file 图片文件
     * @return 结果
     */
    @PostMapping("/uploadImage")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null) {
                return Result.error("文件名不能为空");
            }
            
            String lowerFileName = fileName.toLowerCase();
            if (!(lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || 
                  lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif") || 
                  lowerFileName.endsWith(".webp"))) {
                return Result.error("仅支持 jpg、jpeg、png、gif、webp 格式的图片");
            }
            
            // 验证文件大小 (5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error("图片大小不能超过5MB");
            }
            
            // 上传到MinIO
            String imageUrl = minioService.uploadFile(file, "commentImages");
            return Result.success(imageUrl);
        } catch (Exception e) {
            return Result.error("图片上传失败：" + e.getMessage());
        }
    }

    /**
     * 新增专辑评论
     */
    @PostMapping("/addAlbumComment")
    public Result<String> addAlbumComment(@RequestBody CommentAlbumDTO commentAlbumDTO) {
        return commentService.addAlbumComment(commentAlbumDTO);
    }

    /** 获取专辑评论列表 */
    @GetMapping("/getAlbumComments")
    public Result<java.util.List<CommentVO>> getAlbumComments(@RequestParam Long albumId) {
        return commentService.getAlbumComments(albumId);
    }

    /** 获取歌曲评论列表 */
    @GetMapping("/getSongComments")
    public Result<java.util.List<CommentVO>> getSongComments(@RequestParam Long songId) {
        return commentService.getSongComments(songId);
    }

    /** 获取歌单评论列表 */
    @GetMapping("/getPlaylistComments")
    public Result<java.util.List<CommentVO>> getPlaylistComments(@RequestParam Long playlistId) {
        return commentService.getPlaylistComments(playlistId);
    }

    /**
     * 删除评论
     *
     * @param commentId 评论id
     * @return 结果
     */
    @DeleteMapping("/deleteComment/{id}")
    public Result<String> deleteComment(@PathVariable("id") Long commentId) {
        return commentService.deleteComment(commentId);
    }

}
