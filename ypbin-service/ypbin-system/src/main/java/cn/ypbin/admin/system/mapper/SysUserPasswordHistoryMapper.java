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

import cn.ypbin.admin.system.entity.SysUserPasswordHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 用户历史密码 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserPasswordHistoryMapper extends BaseMapper<SysUserPasswordHistory> {

    /**
     * 批量插入历史密码（单条多值 INSERT）。
     *
     * <p>供 Excel 批量导入等「一次建一批用户」的场景使用：这批用户绕过了
     * {@code SysUserServiceImpl#createUser}，初始密码必须补记历史，否则用户第一次改密时
     * 「新密码不得与最近 N 次重复」查不到初始口令，允许改回管理员下发的同一个密码。
     * 逐条 insert 会随导入行数产生同等数量的往返。</p>
     *
     * <p>主键与审计字段由 MyBatis-Plus 参数处理阶段回填（与 {@code SysUserMapper#insertBatch}
     * 同一机制）；{@code status}/{@code is_deleted} 刻意不写列，让数据库默认值生效。</p>
     *
     * @param rows 待插入行（调用方保证非空）
     * @return 影响行数
     */
    @Insert("<script>"
        + "INSERT INTO sys_user_password_history"
        + " (id, user_id, password, create_user, create_time, update_user, update_time) VALUES "
        + "<foreach collection='rows' item='r' separator=','>"
        + " (#{r.id}, #{r.userId}, #{r.password}, #{r.createUser}, #{r.createTime},"
        + "  #{r.updateUser}, #{r.updateTime})"
        + "</foreach>"
        + "</script>")
    int insertBatch(@Param("rows") List<SysUserPasswordHistory> rows);
}
