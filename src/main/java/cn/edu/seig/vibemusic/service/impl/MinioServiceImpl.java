package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.service.MinioService;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Service
@RefreshScope
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    public MinioServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    private void normalizeConfig() {
        // 规范化 endpoint：
        // 1) 修正可能的单斜杠协议（http:/ -> http://, https:/ -> https://）
        // 2) 去除末尾的斜杠，避免后续拼接出现双斜杠
        if (endpoint != null) {
            if (endpoint.startsWith("http:/") && !endpoint.startsWith("http://")) {
                endpoint = endpoint.replaceFirst("^http:/+", "http://");
            }
            if (endpoint.startsWith("https:/") && !endpoint.startsWith("https://")) {
                endpoint = endpoint.replaceFirst("^https:/+", "https://");
            }
            // 去掉尾部所有斜杠
            endpoint = endpoint.replaceAll("/+$$", "");
        }
    }

    /**
     * 上传文件到 Minio
     *
     * @param file   文件
     * @param folder 文件夹
     * @return 可访问的 URL
     */
    @Override
    public String uploadFile(MultipartFile file, String folder) {
        try {
            // 生成唯一文件名
            String fileName = folder + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

            // 获取文件流
            InputStream inputStream = file.getInputStream();

            // 上传文件
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 返回可访问的 URL
            return endpoint + "/" + bucketName + "/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException(MessageConstant.FILE_UPLOAD + MessageConstant.FAILED + "：" + e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param fileUrl 文件 URL
     */
    @Override
    public void deleteFile(String fileUrl) {
        try {
            // 检查URL是否为空
            if (fileUrl == null || fileUrl.isEmpty()) {
                return;
            }

            String objectKey = extractObjectKey(fileUrl);
            if (objectKey == null || objectKey.isEmpty()) {
                throw new IllegalArgumentException("无法解析文件对象键");
            }

            // 删除文件
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String readText(String fileUrl) {
        try {
            String objectKey = extractObjectKey(fileUrl);
            if (objectKey == null || objectKey.isEmpty()) {
                throw new IllegalArgumentException("无法解析文件对象键");
            }

            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            )) {
                byte[] bytes = in.readAllBytes();
                return smartDecode(bytes);
            }
        } catch (Exception e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public String uploadStream(InputStream inputStream, String originalFilename, String contentType, String folder, long size) {
        try {
            String safeName = originalFilename == null ? "file" : originalFilename;
            String object = folder + "/" + UUID.randomUUID() + "-" + safeName;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(object)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            return endpoint + "/" + bucketName + "/" + object;
        } catch (Exception e) {
            throw new RuntimeException("流上传失败: " + e.getMessage());
        }
    }

    /**
     * 简易智能解码：优先 UTF-8；若中文命中率低或含大量替换符，则回退 GB18030
     */
    private String smartDecode(byte[] bytes) {
        // UTF-8 BOM 处理
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            String s = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
            return s;
        }

        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        int utf8Score = chineseScore(utf8) - replacementPenalty(utf8);

        String gb = new String(bytes, Charset.forName("GB18030"));
        int gbScore = chineseScore(gb) - replacementPenalty(gb);

        return gbScore > utf8Score ? gb : utf8;
    }

    private int chineseScore(String s) {
        int score = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) score++;
        }
        return score;
    }

    private int replacementPenalty(String s) {
        int cnt = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\uFFFD') cnt++;
        return cnt * 5; // 大量替换符扣分
    }

    private boolean isCjk(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') // CJK Unified Ideographs
                || (c >= '\u3400' && c <= '\u4DBF') // CJK Extension A
                || (c >= '\uF900' && c <= '\uFAFF'); // CJK Compatibility Ideographs
    }

    /**
     * 从传入的字符串中提取对象键：
     * - 若传入为完整 URL（即使协议为错误的 http:/ 或 https:/），解析出 /{bucket}/ 之后的部分
     * - 若传入为对象键（不以 http 开头），直接清洗前导斜杠后返回
     */
    private String extractObjectKey(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        String candidate = value.trim();

        // 兼容性修正：将错误的 http:/、https:/ 修正为合法协议，便于 URI 解析
        if (candidate.startsWith("http:/") && !candidate.startsWith("http://")) {
            candidate = candidate.replaceFirst("^http:/+", "http://");
        } else if (candidate.startsWith("https:/") && !candidate.startsWith("https://")) {
            candidate = candidate.replaceFirst("^https:/+", "https://");
        }

        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            try {
                URI uri = new URI(candidate);
                String path = uri.getPath(); // 形如 /bucket/dir/file.ext
                if (path == null) return null;
                // 去掉开头的 '/'
                while (path.startsWith("/")) {
                    path = path.substring(1);
                }
                // 期望以 "bucketName/" 开头
                String bucketPrefix = bucketName + "/";
                if (path.startsWith(bucketPrefix)) {
                    return path.substring(bucketPrefix.length());
                }
                // 若路径中没有 bucket 前缀，但 endpoint 恰好包含路径前缀的情况，做一次回退
                String endpointPrefix = endpoint + "/" + bucketName + "/";
                if (candidate.startsWith(endpointPrefix)) {
                    return candidate.substring(endpointPrefix.length());
                }
                return null;
            } catch (URISyntaxException e) {
                // URI 解析失败，尝试基于字符串前缀降级处理
                String prefix = endpoint + "/" + bucketName + "/";
                if (candidate.startsWith(prefix)) {
                    return candidate.substring(prefix.length());
                }
                // 再尝试匹配 /bucket/ 形式
                int idx = candidate.indexOf("/" + bucketName + "/");
                if (idx >= 0) {
                    return candidate.substring(idx + bucketName.length() + 2);
                }
                return null;
            }
        }

        // 非 URL，视作对象键，移除前导斜杠
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        return candidate;
    }
}
