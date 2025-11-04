package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 手机验证码发送
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1,校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //判断是否符合

        //如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到session
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送短信验证码，验证码为:{}，验证码2分钟内有效",code);

        return Result.ok();
    }

    /**
     * 用户登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result userLogin(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号是否合法
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号有误");
        }

        //校验验证码判断手机号和验证码是否对应
        //不对应
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if(!loginForm.getCode().equals(cacheCode)) {
            return Result.fail("验证码错误");
        }

        //对应,用手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        //判断用户是否存在，不存在就创建，并保存至数据库
        if(user==null) {
            user = createNewUser(loginForm.getPhone());
        }

        //存在,将用户信息保存至redis
        //随机生成token作为key
        String token = UUID.randomUUID().toString(true);

        //将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String hashKey = RedisConstants.LOGIN_USER_KEY+token;
        //存储
        stringRedisTemplate.opsForHash().putAll(hashKey, stringObjectMap);

        //设置tokens有效期
        stringRedisTemplate.expire(hashKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //返回tokens
        return Result.ok(token);
    }

    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户"+RandomUtil.randomNumbers(6));

        save(user);
        return user;
    }


}
