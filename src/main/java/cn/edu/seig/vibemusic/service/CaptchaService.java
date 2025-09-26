package cn.edu.seig.vibemusic.service;

public interface CaptchaService {

    /**
     * 生成验证码，返回 Base64 图片与 key（用 \n 分隔，前者在上层拆分）。
     */
    String generateCaptcha();

    /**
     * 校验验证码是否匹配（大小写不敏感）。
     */
    boolean validate(String key, String userInput);

    /**
     * 删除验证码（验证通过或过期时可清理）。
     */
    void delete(String key);
}


