### 微信扫码登录接入开发文档（YunGouOS + Spring Boot + Vue3）

本文记录本项目接入 YunGouOS 微信扫码登录的完整流程与关键代码，包含后端依赖/配置、接口实现、拦截器放行、前端按钮与回调处理、以及用户创建与头像同步策略。

### 一、后端

#### 1. Maven 依赖
```startLine:endLine:pom.xml
        <!-- YunGouOS 支付/微信登录 SDK -->
        <dependency>
            <groupId>com.yungouos.pay</groupId>
            <artifactId>yungouos-pay-sdk</artifactId>
            <version>2.0.34</version>
        </dependency>

        <!-- fastjson（用于 SDK 参数 JSON 传递） -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.83</version>
        </dependency>
```

#### 2. 配置项
在 `application.yml` 配置 YunGou 商户参数（建议放 Nacos 覆盖）：
```startLine:endLine:src/main/resources/application.yml
# YunGou 配置（建议在 Nacos 中覆盖这些值）
yungou:
  mch-id: ${YUNGOU_MCH_ID:xxx}
  mch-key: ${YUNGOU_MCH_KEY:xxx}
  callback-url: ${YUNGOU_CALLBACK_URL:http://localhost:8090/}
```

属性绑定类：
```startLine:endLine:src/main/java/cn/edu/seig/vibemusic/config/YunGouProperties.java
@Data
@Configuration
@ConfigurationProperties(prefix = "yungou")
public class YunGouProperties {
    private String mchId;
    private String mchKey;
    private String callbackUrl;
}
```

#### 3. 拦截器白名单
放行扫码登录相关路径，避免未登录被拦截：
```startLine:endLine:src/main/java/cn/edu/seig/vibemusic/interceptor/LoginInterceptor.java
        List<String> allowedPaths = Arrays.asList(
                PathConstant.PLAYLIST_DETAIL_PATH,
                PathConstant.ARTIST_DETAIL_PATH,
                PathConstant.SONG_LIST_PATH,
                PathConstant.SONG_DETAIL_PATH,
                PathConstant.ALBUM_DETAIL_PATH,
                "/captcha/**",
                "/yungou/wx/getAuthorizationUrl",
                "/yungou/wx/innerQrCodeInfo",
                "/yungou/wx/loginByCode",
                "/yungou/wx/callback"
        );
```

#### 4. 控制器接口
核心流程：
- 获取授权 URL（PC 扫码跳转）。
- 使用微信回调的 `code` 调取 YunGou 接口换取用户信息。
- 基于 `openId` 查找或创建本地用户：用户名取微信 `nickname`（清洗、去重），头像使用 `headimgurl`，邮箱用 `openId@wx.local` 作为唯一标识。
- 生成 JWT（包含真实 `userId/username/email/openId`）并写入 Redis（6 小时），返回给前端。
- 提供 `/wx/callback` 回调页，负责从 iframe 或当前页跳转到前端路由并携带 `code`。

关键代码：
```startLine:endLine:src/main/java/cn/edu/seig/vibemusic/controller/YunGouController.java
@GetMapping("/wx/getAuthorizationUrl")
public Result<String> getAuthorizationUrl() {
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

@GetMapping("/wx/loginByCode")
public Result<Map<String, Object>> loginByCode(@RequestParam("code") String code) {
    WxOauthInfo wxOauthInfo = WxApi.getWxOauthInfo(
            yunGouProperties.getMchId(), code, yunGouProperties.getMchKey());
    String openId = wxOauthInfo.getOpenId();
    String nick = (wxOauthInfo.getWxUserInfo() != null && wxOauthInfo.getWxUserInfo().getNickname() != null)
            ? wxOauthInfo.getWxUserInfo().getNickname() : "wx_user";
    String wxAvatar = (wxOauthInfo.getWxUserInfo() != null) ? wxOauthInfo.getWxUserInfo().getHeadimgurl() : null;

    // 以 openId 生成稳定邮箱（唯一标识）
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

        // 构造用户：随机密码MD5、头像使用微信头像、邮箱 bindEmail
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
    } else if ((user.getUserAvatar() == null || user.getUserAvatar().isEmpty())
            && wxAvatar != null && !wxAvatar.isEmpty()) {
        // 已存在且头像为空时，补写一次微信头像
        user.setUserAvatar(wxAvatar).setUpdateTime(java.time.LocalDateTime.now());
        userMapper.updateById(user);
    }

    // 生成 JWT，写入 Redis
    Map<String, Object> claims = new HashMap<>();
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

@GetMapping(value = "/wx/callback", produces = MediaType.TEXT_HTML_VALUE)
public String callback(@RequestParam("code") String code,
                       @RequestParam(value = "redirect", required = false) String redirect) {
    String target = (redirect == null || redirect.isEmpty()) ? "/login/wx" : redirect;
    String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
    return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>"
            + "<script>"
            + "(function(){var url='" + target + "?code=" + encodedCode + "';"
            + "if(window.self!==window.top){window.top.location.href=url;}else{window.location.href=url;}})();"
            + "</script></head><body>登录成功，正在跳转...</body></html>";
}
```

辅助函数（用户名清洗、去重、随机密码）位于同控制器底部：
```startLine:endLine:src/main/java/cn/edu/seig/vibemusic/controller/YunGouController.java
private String sanitizeUsername(String input) { ... }
private String ensureUniqueUsername(String base) { ... }
private String randomPassword() { ... }
```

#### 5. 常见问题与处理
- code 只在 5 分钟内有效，过期需重新授权。
- 某些账号可能拿不到 `city/province` 等字段，但 `headimgurl` 通常可用；为空时前端用默认头像兜底。
- 为了不侵入原有登录流程，使用 `openId@wx.local` 作为本地唯一邮箱标识，避免与邮箱注册流程冲突。

---

### 二、前端（Vue3 + Element Plus）

#### 1. 登录页按钮（弹窗内）
在登录表单内新增“微信扫码登录”按钮，请求授权 URL 并跳转：
```startLine:endLine:vibe-music-client-main/src/components/Auth/LoginForm.vue
// 微信扫码登录：获取授权URL并跳转
const handleWxScanLogin = async () => {
  try {
    const res: any = await httpGet('/yungou/wx/getAuthorizationUrl')
    if (res && res.code === 0 && typeof res.data === 'string') {
      window.location.href = res.data
    } else {
      ElMessage.error(res?.message || '获取授权链接失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '获取授权链接失败')
  }
}
```

#### 2. 回调处理（首页统一识别 code）
当 URL 带 `?code=` 时，立即调用后端换取 token，并再请求用户信息填充头像/昵称，写入 Pinia：
```startLine:endLine:vibe-music-client-main/src/pages/index.vue
onMounted(async () => {
  // ...
  try {
    const codeParam = typeof route.query.code === 'string' ? route.query.code : ''
    if (codeParam) {
      const res: any = await httpGet('/yungou/wx/loginByCode', { code: codeParam })
      if (res && res.code === 0) {
        const token = res.data?.token
        if (token) {
          // 先存 token，再拉取用户资料，填充头像/用户名
          user.setUserInfo({}, token)
          try {
            const u = await getUserInfo()
            if (u && u.code === 0) {
              user.setUserInfo(u.data, token)
            }
          } catch {}
          ElMessage.success('登录成功')
          router.replace('/')
        } else {
          ElMessage.error('未获取到登录令牌')
        }
      } else {
        ElMessage.error(res?.message || '登录失败')
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败')
  }
})
```

说明：
- 之所以在首页处理 code，是因为微信可回跳任意路径（我们配置为 `http://localhost:8090/`），首页全局识别可简化前端路由改动。
- 成功后会刷新推荐数据等依赖登录态的模块。

---

### 三、整体时序

1) 前端点击“微信扫码登录”→ 调 `/yungou/wx/getAuthorizationUrl` → 浏览器重定向到微信授权页。
2) 用户在手机微信扫码确认 → 微信回调到配置的 `callback-url`（例如 `http://localhost:8090/?code=...`）。
3) 前端首页检测到 `?code=` → 调 `/yungou/wx/loginByCode` 换 token。
4) 后端使用 YunGou SDK 获取授权信息，按 `openId` 查/建本地用户（用户名取微信昵称，头像用微信头像）→ 生成 JWT，写入 Redis，并返回给前端。
5) 前端保存 token，再调用 `/user/getUserInfo` 更新用户资料 → 顶部导航显示已登录的头像与昵称。

---

### 四、校验与安全建议

- code 有效期 5 分钟；建议前端在拿到 `code` 后立刻调用换取接口。
- 建议在 Nacos 中存放 `yungou.mch-id`、`yungou.mch-key`，避免泄露。
- 推荐新增第三方绑定表（可选）：`tb_user_third_bind(user_id, provider, open_id)`，让账号绑定更清晰（当前用 `openId@wx.local` 作为本地唯一邮箱标识也可满足需求）。
- JWT 过期与 Redis 同步管理：当前设置 6 小时，可按需调整。

---

### 五、测试要点

- 点击“微信扫码登录”后必须出现微信二维码页；若没有，检查后端返回是否为 `data=url`。
- 扫码后应回到站点带有 `?code=...`，并很快显示已登录头像/昵称；若不生效，检查首页回调处理逻辑和网络请求。
- 数据库 `tb_user` 应新增用户记录：`username=微信昵称(清洗后)`，`email=openId@wx.local`，`user_avatar=微信头像URL`。
- Redis 出现对应 JWT token 键；后续接口应正常识别登录态。

