### 图形验证码接入开发文档

以下记录“客户端登录/注册/重置密码”图形验证码的完整接入方案，并附关键代码片段便于复用。

## 一、后端改造

- 依赖
```xml
<!-- easy-captcha -->
<dependency>
  <groupId>com.github.whvcse</groupId>
  <artifactId>easy-captcha</artifactId>
  <version>1.6.2</version>
</dependency>
```

- 服务与控制器
```java
// src/main/java/cn/edu/seig/vibemusic/service/CaptchaService.java
public interface CaptchaService {
    String generateCaptcha();           // 返回 base64 + \n + key
    boolean validate(String key, String userInput);
    void delete(String key);
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/service/impl/CaptchaServiceImpl.java
import com.wf.captcha.SpecCaptcha;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    private static final String KEY_PREFIX = "captcha:";
    private static final long EXPIRE_MINUTES = 10L;
    @Autowired private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public String generateCaptcha() {
        SpecCaptcha captcha = new SpecCaptcha(100, 42); // 避免 JDK17 下 Arithmetic 的脚本引擎问题
        captcha.setLen(4);
        String answer = captcha.text();
        String base64 = captcha.toBase64();
        String key = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + key, answer, EXPIRE_MINUTES, TimeUnit.MINUTES);
        return base64 + "\n" + key;
    }

    @Override
    public boolean validate(String key, String input) {
        if (key == null || input == null) return false;
        Object val = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (val == null) return false;
        return String.valueOf(val).equalsIgnoreCase(input);
    }

    @Override
    public void delete(String key) {
        if (key != null) redisTemplate.delete(KEY_PREFIX + key);
    }
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/controller/CaptchaController.java
@RestController
@RequestMapping("/captcha")
public class CaptchaController {
    @Autowired private CaptchaService captchaService;

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
```

- 放行与拦截配置
```java
// src/main/java/cn/edu/seig/vibemusic/config/WebConfig.java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(loginInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(
            "/admin/login", "/admin/logout", "/admin/register",
            "/user/login", "/user/logout", "/user/register",
            "/user/sendVerificationCode", "/user/resetUserPassword",
            "/captcha/**",                                  // 放行验证码
            "/banner/getBannerList",
            "/playlist/getAllPlaylists", "/playlist/getRecommendedPlaylists", "/playlist/getPlaylistDetail/**",
            "/artist/getAllArtists", "/artist/getArtistDetail/**",
            "/song/getAllSongs", "/song/getRecommendedSongs", "/song/getSongDetail/**");
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/interceptor/LoginInterceptor.java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if (request.getMethod().equalsIgnoreCase("OPTIONS")) { response.setStatus(200); return true; }
    String path = request.getRequestURI();
    // 验证码接口优先放行
    if (path != null && path.startsWith("/captcha/")) return true;
    // ...（其余鉴权逻辑）
}
```

- 用户接口校验（举例：登录）
```java
// src/main/java/cn/edu/seig/vibemusic/controller/UserController.java（片段）
@PostMapping("/login")
public Result login(@RequestBody @Valid UserLoginDTO userLoginDTO, BindingResult br) {
    String err = BindingResultUtil.handleBindingResultErrors(br);
    if (err != null) return Result.error(err);
    if (userLoginDTO.getCheckCodeKey() == null || userLoginDTO.getCheckCode() == null
        || !captchaValidate(userLoginDTO.getCheckCodeKey(), userLoginDTO.getCheckCode())) {
        return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
    }
    return userService.login(userLoginDTO);
}

private boolean captchaValidate(String key, String code) {
    boolean ok = captchaService.validate(key, code);
    if (ok) captchaService.delete(key); // 用后删除
    return ok;
}
```

- DTO 新增字段（登录/注册/重置）
```java
// 以登录为例：src/main/java/cn/edu/seig/vibemusic/model/dto/UserLoginDTO.java
private String checkCodeKey;
private String checkCode;
```

## 二、前端改造（vibe-music-client-main）

- API
```ts
// src/api/system.ts
export const getCaptcha = () => http<Result>('get', '/captcha/generate')
```

- Axios 请求拦截器：对验证码不加 token
```ts
// src/utils/http.ts（片段）
if (config.url?.includes('/user/login') || config.url?.includes('/captcha/generate')) {
  return config; // 不加 Authorization
}
```

- 登录表单（注册/重置同理）
```vue
<!-- src/components/Auth/LoginForm.vue 关键片段 -->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getCaptcha } from '@/api/system'
const loginForm = reactive({ email: '', password: '', checkCode: '', checkCodeKey: '' })
const captcha = reactive({ image: '', loading: false })

const refreshCaptcha = async () => {
  if (captcha.loading) return
  captcha.loading = true
  try {
    const res = await getCaptcha()
    if (res && res.code === 0 && res.data) {
      const data: any = res.data
      captcha.image = data.checkCode
      loginForm.checkCodeKey = data.checkCodeKey
      loginForm.checkCode = ''
    }
  } finally {
    captcha.loading = false
  }
}
onMounted(refreshCaptcha)
</script>

<template>
  <!-- 原有邮箱/密码... -->
  <el-form-item prop="checkCode" class="mt-6">
    <div style="display:flex; gap:8px; align-items:center; width:100%">
      <el-input v-model="loginForm.checkCode" placeholder="图形验证码" />
      <img v-if="captcha.image" :src="captcha.image" class="captcha-img" @click="refreshCaptcha" title="点击刷新" />
      <el-button @click="refreshCaptcha" :loading="captcha.loading">换一张</el-button>
    </div>
  </el-form-item>
</template>
```

- 只渲染当前激活的表单，避免并发请求
```vue
<!-- src/components/Auth/AuthTabs.vue（片段） -->
<LoginForm v-if="activeTab === 'login'" @success="..." @switch-tab="..." />
<RegisterForm v-if="activeTab === 'register'" ... />
<ResetPasswordForm v-if="activeTab === 'reset'" ... />
```

## 三、调用与时序

1) 前端拉取验证码 `/captcha/generate` → 得到 `checkCode(Base64)` 与 `checkCodeKey`
2) 展示图像，用户输入 `checkCode`
3) 提交登录/注册/重置时附带 `checkCodeKey` + `checkCode`
4) 后端校验通过后删除 Redis 键，避免重放

## 四、常见问题

- 401 未登录获取不到验证码
  - 后端需放行 `/captcha/**`；前端不要给 `/captcha/generate` 加 Authorization
- JDK17 下 `ArithmeticCaptcha` 报 NPE
  - 改用 `SpecCaptcha`（字符型），不依赖脚本引擎
- 弹窗加载三次验证码
  - 仅渲染当前 Tab 表单（`v-if`）

## 五、验收清单

- 未登录直接 GET `/captcha/generate` → 200 + Base64图片
- 登录/注册/重置页面展示验证码，点击“换一张”可刷新
- 错误验证码返回“验证码无效”
- 验证通过后 Redis 对应 key 被删除
- 开发者工具中 `/captcha/generate` 请求头无 Authorization

将以上片段与说明保存到你的开发笔记即可快速复用。需要我输出 Markdown 文件版本或含图片示意的 README 版，也可以继续说明。