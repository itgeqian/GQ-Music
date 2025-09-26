package cn.edu.seig.vibemusic.controller;


import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.model.dto.*;
import cn.edu.seig.vibemusic.model.vo.UserVO;
import cn.edu.seig.vibemusic.model.vo.UserProfileVO;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.service.IUserService;
import cn.edu.seig.vibemusic.service.MinioService;
import cn.edu.seig.vibemusic.service.CaptchaService;
import cn.edu.seig.vibemusic.util.BindingResultUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;
    @Autowired
    private MinioService minioService;
    @Autowired
    private CaptchaService captchaService;

    /**
     * 发送验证码
     *
     * @param email 邮箱
     * @return 结果
     */
    @GetMapping("/sendVerificationCode")
    public Result sendVerificationCode(@RequestParam @Email String email) {
        return userService.sendVerificationCode(email);
    }

    /**
     * 注册
     *
     * @param userRegisterDTO 用户注册信息
     * @param bindingResult   绑定结果
     * @return 结果
     */
    @PostMapping("/register")
    public Result register(@RequestBody @Valid UserRegisterDTO userRegisterDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        // 图形验证码校验
        if (userRegisterDTO.getCheckCodeKey() == null || userRegisterDTO.getCheckCode() == null
                || !captchaValidate(userRegisterDTO.getCheckCodeKey(), userRegisterDTO.getCheckCode())) {
            return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
        }

        // 邮箱验证码校验
        boolean isCodeValid = userService.verifyVerificationCode(userRegisterDTO.getEmail(), userRegisterDTO.getVerificationCode());
        if (!isCodeValid) {
            return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
        }

        return userService.register(userRegisterDTO);
    }

    /**
     * 登录
     *
     * @param userLoginDTO  用户登录信息
     * @param bindingResult 绑定结果
     * @return 结果
     */
    @PostMapping("/login")
    public Result login(@RequestBody @Valid UserLoginDTO userLoginDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        // 图形验证码校验
        if (userLoginDTO.getCheckCodeKey() == null || userLoginDTO.getCheckCode() == null
                || !captchaValidate(userLoginDTO.getCheckCodeKey(), userLoginDTO.getCheckCode())) {
            return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
        }

        return userService.login(userLoginDTO);
    }

    /**
     * 获取用户信息
     *
     * @return 结果
     */
    @GetMapping("/getUserInfo")
    public Result<UserVO> getUserInfo() {
        return userService.userInfo();
    }

    /** 公开：根据用户ID获取用户资料（含粉丝/关注数/是否已关注） */
    @GetMapping("/profile")
    public Result<UserProfileVO> getUserProfile(@RequestParam("id") Long profileUserId) {
        return userService.getUserProfile(profileUserId);
    }

    /** 关注用户 */
    @PostMapping("/follow")
    public Result<String> follow(@RequestParam("id") Long targetUserId) {
        return userService.followUser(targetUserId);
    }

    /** 取消关注用户 */
    @DeleteMapping("/unfollow")
    public Result<String> unfollow(@RequestParam("id") Long targetUserId) {
        return userService.cancelFollowUser(targetUserId);
    }

    /** 是否已关注该用户 */
    @GetMapping("/isFollowed")
    public Result<Boolean> isFollowed(@RequestParam("id") Long targetUserId) {
        return userService.isUserFollowed(targetUserId);
    }

    /** 我关注的用户（如果提供 userId 则返回该用户关注的人；否则默认当前登录用户） */
    @GetMapping("/following")
    public Result<List<UserVO>> getFollowing(@RequestParam(value = "userId", required = false) Long userId) {
        return userService.getFollowing(userId);
    }

    /** 关注我的用户（如果提供 userId 则返回该用户的粉丝；否则默认当前登录用户） */
    @GetMapping("/followers")
    public Result<List<UserVO>> getFollowers(@RequestParam(value = "userId", required = false) Long userId) {
        return userService.getFollowers(userId);
    }

    /** 搜索用户 */
    @GetMapping("/search")
    public Result<PageResult<UserVO>> searchUsers(@RequestParam("keyword") String keyword,
                                                  @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                  @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return userService.searchUsers(keyword, pageNum, pageSize);
    }

    /**
     * 更新用户信息
     *
     * @param userDTO 用户信息
     * @return 结果
     */
    @PutMapping("/updateUserInfo")
    public Result updateUserInfo(@RequestBody @Valid UserDTO userDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return userService.updateUserInfo(userDTO);
    }

    /**
     * 更新用户头像
     *
     * @param avatar 头像
     * @return 结果
     */
    @PatchMapping("/updateUserAvatar")
    public Result updateUserAvatar(@RequestParam("avatar") MultipartFile avatar) {
        String avatarUrl = minioService.uploadFile(avatar, "users");  // 上传到 users 目录
        return userService.updateUserAvatar(avatarUrl);
    }

    /**
     * 更新用户密码
     *
     * @param userPasswordDTO 用户密码信息
     * @param token           认证token
     * @return 结果
     */
    @PatchMapping("/updateUserPassword")
    public Result updateUserPassword(@RequestBody @Valid UserPasswordDTO userPasswordDTO,
                                     @RequestHeader("Authorization") String token, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return userService.updateUserPassword(userPasswordDTO, token);
    }

    /**
     * 重置用户密码
     *
     * @param userResetPasswordDTO 用户密码信息
     * @return 结果
     */
    @PatchMapping("/resetUserPassword")
    public Result resetUserPassword(@RequestBody @Valid UserResetPasswordDTO userResetPasswordDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        // 图形验证码校验
        if (userResetPasswordDTO.getCheckCodeKey() == null || userResetPasswordDTO.getCheckCode() == null
                || !captchaValidate(userResetPasswordDTO.getCheckCodeKey(), userResetPasswordDTO.getCheckCode())) {
            return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
        }

        // 邮箱验证码校验
        boolean isCodeValid = userService.verifyVerificationCode(userResetPasswordDTO.getEmail(), userResetPasswordDTO.getVerificationCode());
        if (!isCodeValid) {
            return Result.error(MessageConstant.VERIFICATION_CODE + MessageConstant.INVALID);
        }

        return userService.resetUserPassword(userResetPasswordDTO);
    }

    /**
     * 登出
     *
     * @param token 认证token
     * @return 结果
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return userService.logout(token);
    }

    /**
     * 注销账号
     *
     * @return 结果
     */
    @DeleteMapping("/deleteAccount")
    public Result deleteAccount() {
        return userService.deleteAccount();
    }

    private boolean captchaValidate(String key, String code) {
        boolean ok = captchaService.validate(key, code);
        if (ok) {
            captchaService.delete(key);
        }
        return ok;
    }
}
