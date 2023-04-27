package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    //    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取 session
//        HttpSession session = request.getSession();
//
//        // 2. 获取 session 中的用户
//        Object user = session.getAttribute("user");
//
//        // 3. 判断用户是否存在
//        if (null == user) {
//            // 4. 不存在，拦截，返回 401 状态码
//            response.setStatus(401);
//            return false;
//        }
//
//        // 5. 存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
//
//        // 6. 放行
//        return true;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 有了 RefreshTokenInterceptor 拦截器，这里只需要判断是否拦截
        if (UserHolder.getUser() == null) {
            // 未登录用户，需要拦截，设置状态码
            response.setStatus(401);
            return false;
        }
        // 查询到用户，直接放行
        return true;
    }
}
