package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    /**
     * 生成图形验证码
     */
    @GetMapping("/generate")
    public Result<Map<String, String>> generate() {
        String merged = captchaService.generateCaptcha();
        int idx = merged.indexOf('\n');
        String base64 = merged.substring(0, idx);
        String key = merged.substring(idx + 1);
        Map<String, String> data = new HashMap<>();
        data.put("checkCode", base64);
        data.put("checkCodeKey", key);
        return Result.success(data);
    }
}


