package cn.edu.seig.vibemusic.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface MinioService {
    /**
     * 上传文件到 MinIO
     * @param file   要上传的文件
     * @param folder 存储文件的目录
     * @return 文件访问 URL
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * 删除 MinIO 文件
     * @param fileUrl 文件 URL
     */
    void deleteFile(String fileUrl);

    /**
     * 读取文本文件内容（UTF-8）
     * @param fileUrl 文件 URL 或对象键
     * @return 文本内容
     */
    String readText(String fileUrl);

    /**
     * 通过输入流上传（用于临时文件/生成的海报等）
     * @param inputStream 数据流
     * @param originalFilename 原始文件名（用于生成对象名与 content-disposition）
     * @param contentType MIME 类型
     * @param folder 目录
     * @param size 大小（可传 -1 使用未知长度）
     * @return 可访问的 URL
     */
    String uploadStream(InputStream inputStream, String originalFilename, String contentType, String folder, long size);

}
