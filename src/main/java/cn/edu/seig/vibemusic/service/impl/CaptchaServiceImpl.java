package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.service.CaptchaService;
import com.wf.captcha.SpecCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final long EXPIRE_MINUTES = 10L;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public String generateCaptcha() {
        // 使用字符型验证码，避免 JDK17 下 ArithmeticCaptcha 依赖的脚本引擎缺失导致 NPE
        SpecCaptcha captcha = new SpecCaptcha(100, 42);
        captcha.setLen(4); // 4 位
        String answer = captcha.text();
        String base64 = captcha.toBase64();
        String key = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + key, answer, EXPIRE_MINUTES, TimeUnit.MINUTES);
        return base64 + "\n" + key;
    }

    @Override
    public boolean validate(String key, String userInput) {
        if (key == null || key.isEmpty() || userInput == null) return false;
        Object val = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (val == null) return false;
        String real = String.valueOf(val);
        return real.equalsIgnoreCase(userInput);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isEmpty()) return;
        redisTemplate.delete(KEY_PREFIX + key);
    }
}


