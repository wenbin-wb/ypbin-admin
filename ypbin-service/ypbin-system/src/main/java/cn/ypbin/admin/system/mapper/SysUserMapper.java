/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 批量插入用户（单条多值 INSERT）。
     *
     * <p>Excel 批量导入逐行 insert 时，N 行就是 N 次数据库往返（同一事务内）；改为一次多值 INSERT。
     * 主键与审计字段的填充与内置 {@code insert} 走同一套 MyBatis-Plus 参数处理
     * （{@code IdType.ASSIGN_ID} 回填 id、{@code MetaObjectHandler} 填 create_user/create_time），
     * 故批量路径与逐条路径的落库结果一致。</p>
     *
     * @param rows 待插入用户（调用方保证非空）
     * @return 影响行数
     */
    @Insert("<script>"
        + "INSERT INTO sys_user"
        + " (id, tenant_id, username, user_type, password, real_name, nickname, dept_id, avatar, phone,"
        + "  email, gender, remark, last_login_time, pwd_reset_time, create_user, create_time,"
        + "  update_user, update_time, status, is_deleted) VALUES "
        + "<foreach collection='rows' item='r' separator=','>"
        + " (#{r.id}, #{r.tenantId}, #{r.username}, #{r.userType}, #{r.password}, #{r.realName}, #{r.nickname},"
        + "  #{r.deptId}, #{r.avatar}, #{r.phone}, #{r.email}, #{r.gender}, #{r.remark}, #{r.lastLoginTime},"
        + "  #{r.pwdResetTime}, #{r.createUser}, #{r.createTime}, #{r.updateUser}, #{r.updateTime},"
        + "  #{r.status}, #{r.isDeleted})"
        + "</foreach>"
        + "</script>")
    int insertBatch(@Param("rows") List<SysUser> rows);

}
