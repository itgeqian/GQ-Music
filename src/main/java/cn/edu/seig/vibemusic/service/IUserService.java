package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.*;
import cn.edu.seig.vibemusic.model.entity.User;
import cn.edu.seig.vibemusic.model.vo.UserManagementVO;
import cn.edu.seig.vibemusic.model.vo.UserVO;
import cn.edu.seig.vibemusic.model.vo.UserProfileVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
public interface IUserService extends IService<User> {

    // 发送验证码
    Result sendVerificationCode(String email);

    // 验证验证码
    boolean verifyVerificationCode(String email, String verificationCode);

    // 用户注册
    Result register(UserRegisterDTO userRegisterDTO);

    // 用户登录
    Result login(UserLoginDTO userLoginDTO);

    // 用户信息
    Result<UserVO> userInfo();

    // 公开：根据用户ID获取用户资料（含粉丝/关注数）
    Result<UserProfileVO> getUserProfile(Long profileUserId);

    // 关注用户
    Result<String> followUser(Long targetUserId);

    // 取消关注用户
    Result<String> cancelFollowUser(Long targetUserId);

    // 是否已关注用户
    Result<Boolean> isUserFollowed(Long targetUserId);

    // 更新用户信息
    Result updateUserInfo(UserDTO userDTO);

    // 更新用户头像
    Result updateUserAvatar(String avatarUrl);

    // 更新用户密码
    Result updateUserPassword(UserPasswordDTO userPasswordDTO, String token);

    // 重置用户密码
    Result resetUserPassword(UserResetPasswordDTO userResetPasswordDTO);

    // 退出登录
    Result logout(String token);

    // 注销账号
    Result deleteAccount();

    // 获取所有用户数量
    Result<Long> getAllUsersCount();

    // 获取所有用户
    Result<PageResult<UserManagementVO>> getAllUsers(UserSearchDTO userSearchDTO);

    // 搜索用户
    Result<PageResult<UserVO>> searchUsers(String keyword, Integer pageNum, Integer pageSize);

    // 添加用户
    Result addUser(UserAddDTO userAddDTO);

    // 更新用户
    Result updateUser(UserDTO userDTO);

    // 更新用户状态
    Result updateUserStatus(Long userId, Integer userStatus);

    // 删除用户
    Result deleteUser(Long userId);

    // 批量删除用户
    Result deleteUsers(List<Long> userIds);

    // 我关注的用户（userId 为空时默认当前登录用户）
    Result<List<UserVO>> getFollowing(Long userId);

    // 关注我的用户（userId 为空时默认当前登录用户）
    Result<List<UserVO>> getFollowers(Long userId);
}
