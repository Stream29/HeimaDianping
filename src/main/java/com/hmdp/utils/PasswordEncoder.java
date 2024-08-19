package com.hmdp.utils;


import cn.hutool.core.util.RandomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class PasswordEncoder {

    public static @NotNull String encode(String password) {
        // 生成盐
        String salt = RandomUtil.randomString(20);
        // 加密
        return encode(password,salt);
    }
    private static @NotNull String encode(String password, String salt) {
        // 加密
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    public static @NotNull Boolean matches(@Nullable String encodedPassword, @Nullable String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("密码格式不正确！");
        }
        String[] arr = encodedPassword.split("@");
        // 获取盐
        String salt = arr[0];
        // 比较
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
