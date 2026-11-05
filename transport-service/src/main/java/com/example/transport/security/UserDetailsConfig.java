package com.example.transport.security;

import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserDetailsConfig {
    // Tạo user với role ADMIN
    // Đã chuyển việc tạo ADMIN ban đầu sang AccountBootstrap và lưu trong PostgreSQL.
    // Tạo user với role USER
    // Tài khoản USER cũng được lưu trong PostgreSQL; không còn tài khoản/mật khẩu mẫu trong mã nguồn.

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
