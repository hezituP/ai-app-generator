package com.hezitu.heaicodemother.aop;


import com.hezitu.heaicodemother.annotation.AuthCheck;
import com.hezitu.heaicodemother.model.entity.User;
import com.hezitu.heaicodemother.model.enums.UserRoleEnum;
import com.hezitu.heaicodemother.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Retention;
@Aspect
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 拦截器，判断用户是否登录以及是否具有访问权限，是比较简单的实现，实际项目中可能需要更复杂的权限校验逻辑
     * @param joinPoint
     * @param authCheck
     * @return
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 获取当前用户的登录状态
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        //不需要权限，直接放行就可以
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 需要权限，判断用户是否登录
        if (loginUser == null) {
            throw new RuntimeException("请登录后重试");
        }
        // 校验权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !loginUser.getUserRole().equals(UserRoleEnum.ADMIN.getValue())) {
            throw new RuntimeException("无权限访问");
        }
        // 通过权限校验，放行
        return joinPoint.proceed();

    }
}
