package cn.edu.seig.vibemusic.controller;


import cn.edu.seig.vibemusic.config.YunGouProperties;
import cn.edu.seig.vibemusic.result.Result;
import com.alibaba.fastjson.JSONObject;
import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.enumeration.RoleEnum;
import cn.edu.seig.vibemusic.enumeration.UserStatusEnum;
import cn.edu.seig.vibemusic.mapper.UserMapper;
import cn.edu.seig.vibemusic.model.entity.User;
import cn.edu.seig.vibemusic.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.concurrent.TimeUnit;
import com.yungouos.pay.entity.WxOauthInfo;
import com.yungouos.pay.entity.WxWebLoginBiz;
import com.yungouos.pay.wxapi.WxApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/yungou")
public class YunGouController {

    @Autowired
    private YunGouProperties yunGouProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;

    /**
     * 获取授权Url链接（PC 扫码跳转方式）
     */
    @GetMapping("/wx/getAuthorizationUrl")
    public Result<String> getAuthorizationUrl() {
        log.info("[YunGou] getAuthorizationUrl called");
        JSONObject params = new JSONObject();
        String url = WxApi.getWxOauthUrl(
                yunGouProperties.getMchId(),
                yunGouProperties.getCallbackUrl(),
                "open-url",
                params,
                yunGouProperties.getMchKey()
        );
        return Result.success("获取成功", url);
    }

    /**
     * 根据授权回调的 code 获取用户信息（简化 token：openId）
     */
    @GetMapping("/wx/loginByCode")
    public Result<Map<String, Object>> loginByCode(@RequestParam("code") String code) {
        log.info("[YunGou] loginByCode, code={}", code);
        WxOauthInfo wxOauthInfo = WxApi.getWxOauthInfo(
                yunGouProperties.getMchId(),
                code,
                yunGouProperties.getMchKey()
        );

        // 1) 依据 openId 查找/创建本地用户
        String openId = wxOauthInfo.getOpenId();
        String nick = (wxOauthInfo.getWxUserInfo() != null && wxOauthInfo.getWxUserInfo().getNickname() != null)
                ? wxOauthInfo.getWxUserInfo().getNickname() : "wx_user";
        String wxAvatar = null;
        try {
            // SDK 字段为 headimgurl
            wxAvatar = (wxOauthInfo.getWxUserInfo() != null) ? wxOauthInfo.getWxUserInfo().getHeadimgurl() : null;
        } catch (Exception ignored) {}

        // 以 openId 生成稳定邮箱（满足非空、唯一约束）
        String bindEmail = openId + "@wx.local";
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", bindEmail));
        if (user == null) {
            // 生成合法且唯一的用户名（4-16位，仅 a-zA-Z0-9_-）
            String username = sanitizeUsername(nick);
            if (username.length() < 4) {
                String suffix = openId.length() > 6 ? openId.substring(openId.length() - 6) : openId;
                username = (username + "_" + suffix).replaceAll("[^a-zA-Z0-9_-]", "");
                if (username.length() > 16) username = username.substring(0, 16);
                if (username.length() < 4) username = ("wx_" + suffix).substring(0, Math.min(16, ("wx_" + suffix).length()));
            }
            username = ensureUniqueUsername(username);

            // 构造用户：密码随机（MD5 存库），头像留空（前端显示默认头像），邮箱为 bindEmail
            String rawPwd = randomPassword();
            String passwordMD5 = DigestUtils.md5DigestAsHex(rawPwd.getBytes());
            user = new User();
            user.setUsername(username)
                .setPassword(passwordMD5)
                .setEmail(bindEmail)
                .setUserAvatar(wxAvatar)
                .setCreateTime(java.time.LocalDateTime.now())
                .setUpdateTime(java.time.LocalDateTime.now())
                .setUserStatus(UserStatusEnum.ENABLE);
            userMapper.insert(user);
        } else if ((user.getUserAvatar() == null || user.getUserAvatar().isEmpty()) && wxAvatar != null && !wxAvatar.isEmpty()) {
            // 已存在且头像为空时，补写一次微信头像
            user.setUserAvatar(wxAvatar).setUpdateTime(java.time.LocalDateTime.now());
            userMapper.updateById(user);
        }

        // 2) 生成 JWT（带真实 userId / username / email），并写入 Redis
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(JwtClaimsConstant.ROLE, RoleEnum.USER.getRole());
        claims.put(JwtClaimsConstant.USER_ID, user.getUserId());
        claims.put(JwtClaimsConstant.USERNAME, user.getUsername());
        claims.put(JwtClaimsConstant.EMAIL, user.getEmail());
        claims.put("openId", openId);
        String token = JwtUtil.generateToken(claims);
        stringRedisTemplate.opsForValue().set(token, token, 6, TimeUnit.HOURS);

        Map<String, Object> data = new HashMap<>();
        data.put("wxOauthInfo", wxOauthInfo);
        data.put("token", token);
        return Result.success(data);
    }

    /**
     * 获取内嵌二维码信息（前端使用 WxLogin 渲染登录二维码）
     */
    @GetMapping("/wx/innerQrCodeInfo")
    public Result<Object> qrCodeInfo() {
        log.info("[YunGou] innerQrCodeInfo called");
        JSONObject params = new JSONObject();
        WxWebLoginBiz loginBiz = WxApi.getWebLogin(
                yunGouProperties.getMchId(),
                yunGouProperties.getCallbackUrl(),
                params,
                yunGouProperties.getMchKey()
        );
        return Result.success(loginBiz);
    }

    /**
     * 回调页面（便于内嵌 iframe 回跳顶层窗口并把 code 带回前端路由）
     * 默认跳转到 /login/wx?code=xxx，前端可在路由页调用 /yungou/wx/loginByCode 完成登录
     */
    @GetMapping(value = "/wx/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam("code") String code,
                           @RequestParam(value = "redirect", required = false) String redirect) {
        String target = (redirect == null || redirect.isEmpty()) ? "/login/wx" : redirect;
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        // 在 iframe 中让父窗口跳转；如果不是 iframe 也直接跳转
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>" +
                "<script>" +
                "(function(){var url='" + target + "?code=" + encodedCode + "';" +
                "if(window.self!==window.top){window.top.location.href=url;}else{window.location.href=url;}})();" +
                "</script></head><body>登录成功，正在跳转...</body></html>";
    }

    private String genToken(WxOauthInfo wxOauthInfo) {
        log.info("[YunGou] wxOauthInfo: {}", wxOauthInfo);
        // 生成与项目一致的 JWT，最小声明：角色=USER，用户名=微信昵称，email 可空，userId 暂无
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.ROLE, RoleEnum.USER.getRole());
        claims.put(JwtClaimsConstant.USERNAME, wxOauthInfo.getWxUserInfo() != null ? wxOauthInfo.getWxUserInfo().getNickname() : "wx_user");
        // 非绑定阶段：不产生真实 userId，放置 0 作为占位，避免服务端对 USER_ID 的空指针
        claims.put(JwtClaimsConstant.USER_ID, 0L);
        // 临时将 openId 放入 claims，便于后续绑定逻辑使用
        claims.put("openId", wxOauthInfo.getOpenId());

        String token = JwtUtil.generateToken(claims);
        // 将 token 写入 Redis，与现有拦截器逻辑保持一致（6 小时）
        stringRedisTemplate.opsForValue().set(token, token, 6, TimeUnit.HOURS);
        return token;
    }

    private String sanitizeUsername(String input) {
        String s = input == null ? "wx_user" : input;
        // 只保留 a-zA-Z0-9_-
        s = s.replaceAll("[^a-zA-Z0-9_-]", "");
        if (s.length() > 16) s = s.substring(0, 16);
        return s;
    }

    private String ensureUniqueUsername(String base) {
        String candidate = base;
        int i = 1;
        while (userMapper.selectOne(new QueryWrapper<User>().eq("username", candidate)) != null) {
            String suffix = "_" + i;
            int allowed = Math.max(4, 16 - suffix.length());
            String head = base.length() > allowed ? base.substring(0, allowed) : base;
            candidate = head + suffix;
            i++;
            if (i > 9999) break;
        }
        return candidate;
    }

    private String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$_-";
        java.util.Random r = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}


