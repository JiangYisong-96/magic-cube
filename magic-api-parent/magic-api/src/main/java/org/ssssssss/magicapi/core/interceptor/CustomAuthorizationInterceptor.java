package org.ssssssss.magicapi.core.interceptor;

import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.MagicUser;
import org.ssssssss.magicapi.core.exception.MagicLoginException;
import org.ssssssss.magicapi.core.model.Group;
import org.ssssssss.magicapi.core.model.MagicEntity;
import org.ssssssss.magicapi.utils.MD5Utils;

/**
 * 自定义操作鉴权
 */
@Component  //注入到Spring容器中
public class CustomAuthorizationInterceptor implements AuthorizationInterceptor {

    private final boolean requireLogin;
    private String validToken;
    private MagicUser configMagicUser;

    public CustomAuthorizationInterceptor() {
        this.requireLogin = false;
    }

    /**
     * 配置是否需要登录
     */
    @Override
    public boolean requireLogin() {
        return this.requireLogin;
    }

    /**
     * 是否拥有页面按钮的权限
     */
    @Override
    public boolean allowVisit(MagicUser magicUser, HttpServletRequest request,
        Authorization authorization) {
        // Authorization.SAVE 保存
        // Authorization.DELETE 删除
        // Authorization.VIEW 查询
        // Authorization.LOCK 锁定
        // Authorization.UNLOCK 解锁
        // Authorization.DOWNLOAD 导出
        // Authorization.UPLOAD 上传
        // Authorization.PUSH 推送
        return true;
    }

    /**
     * 是否拥有对该接口的增删改权限 此方法可以不重写，则走默认的 boolean allowVisit(MagicUser magicUser, HttpServletRequest
     * request, Authorization authorization) 方法
     */
    @Override
    public boolean allowVisit(MagicUser magicUser, HttpServletRequest request,
        Authorization authorization, MagicEntity entity) {
        // Authorization.SAVE 保存
        // Authorization.DELETE 删除
        // Authorization.VIEW 查询
        // Authorization.LOCK 锁定
        // Authorization.UNLOCK 解锁
        // 自行写逻辑判断是否拥有如果有，则返回true，反之为false
        return true;
    }

    /**
     * 是否拥有对该分组的增删改权限 此方法可以不重写，则走默认的 boolean allowVisit(MagicUser magicUser, HttpServletRequest
     * request, Authorization authorization) 方法
     */
    @Override
    public boolean allowVisit(MagicUser magicUser, HttpServletRequest request,
        Authorization authorization, Group group) {
        // Authorization.SAVE 保存
        // Authorization.DELETE 删除
        // Authorization.VIEW 查询
        // 自行写逻辑判断是否拥有如果有，则返回true，反之为false
        return true;
    }

    @Override
    public MagicUser getUserByToken(String token) throws MagicLoginException {
        if (requireLogin && Objects.equals(validToken, token)) {
            return configMagicUser;
        }
        throw new MagicLoginException("token无效");
    }

    @Override
    public MagicUser login(String username, String password) throws MagicLoginException {
        if (requireLogin && Objects.equals(
            MD5Utils.encrypt(String.format("%s||%s", username, password)), this.validToken)) {
            return configMagicUser;
        }
        if (this.configMagicUser == null) {

        }
        throw new MagicLoginException("用户名或密码不正确");
    }

}