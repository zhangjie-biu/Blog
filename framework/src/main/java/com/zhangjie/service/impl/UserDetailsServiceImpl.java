package com.zhangjie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjie.constants.SystemConstants;
import com.zhangjie.domain.entity.LoginUser;
import com.zhangjie.domain.entity.User;
import com.zhangjie.mapper.MenuMapper;
import com.zhangjie.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    MenuMapper menuMapper;

    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //根据用户名查询用户信息
        LambdaQueryWrapper<User> lambdaQueryWrapper =new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getUserName,s);
        User user = userMapper.selectOne(lambdaQueryWrapper);
        //判断是否查询到用户 没查询到抛出异常
        if(Objects.isNull(user)){
            throw new RuntimeException("用户不存在");
        }
        // 查询权限信息封装
        if(user.getType().equals(SystemConstants.ADMIN)){
            List<String> perms = menuMapper.selectPermsByUserId(user.getId());
            return new LoginUser(user,perms);
        }
        //查询到用户返回用户信息

        return new LoginUser(user,null);
    }
}
