package com.iflytek.astron.console.commons.config;

import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.enums.space.EnterpriseServiceTypeEnum;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class MockUserFilter extends OncePerRequestFilter {
    private final UserInfoDataService userInfoDataService;

    public static final String USER_ID_ATTRIBUTE = "X-User-Id";
    public static final String USER_INFO_ATTRIBUTE = "X-User-Info";

    private static final int DEFAULT_ACCOUNT_STATUS = 1;
    private static final int DEFAULT_USER_AGREEMENT = 0;
    private static final int DEFAULT_DELETED = 0;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String userId = "admin";
        request.setAttribute(USER_ID_ATTRIBUTE, userId);

        UserInfo userInfo = new UserInfo();
        userInfo.setUid(userId);
        userInfo.setUsername("admin");
        userInfo.setAvatar(null);
        userInfo.setMobile(null);
        userInfo.setAccountStatus(DEFAULT_ACCOUNT_STATUS);
        userInfo.setEnterpriseServiceType(EnterpriseServiceTypeEnum.NONE);
        userInfo.setUserAgreement(DEFAULT_USER_AGREEMENT);
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setDeleted(DEFAULT_DELETED);

        UserInfo existingUser = userInfoDataService.createOrGetUser(userInfo);
        request.setAttribute(USER_INFO_ATTRIBUTE, existingUser);

        filterChain.doFilter(request, response);
    }
}
