package cn.edu.seig.vibemusic.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 角色权限管理器
 */
@Component
public class RolePermissionManager {

    private final RolePathPermissionsConfig rolePathPermissionsConfig;

    @Autowired
    public RolePermissionManager(RolePathPermissionsConfig rolePathPermissionsConfig) {
        this.rolePathPermissionsConfig = rolePathPermissionsConfig;
    }

    // 判断当前角色是否有权限访问请求的路径
    public boolean hasPermission(String role, String requestURI) {
        Map<String, List<String>> permissions = rolePathPermissionsConfig.getPermissions();
        List<String> allowedPaths = permissions.get(role);
        // 默认兜底：主题相关路径允许登录用户访问（防止外部配置未同步导致403）
        if (requestURI != null && (requestURI.startsWith("/theme/") || requestURI.startsWith("/user/theme/"))) {
            return true;
        }
        if (allowedPaths != null) {
            for (String path : allowedPaths) {
                if (requestURI.startsWith(path)) {
                    return true;
                }
            }
        }
        return false;
    }
}

