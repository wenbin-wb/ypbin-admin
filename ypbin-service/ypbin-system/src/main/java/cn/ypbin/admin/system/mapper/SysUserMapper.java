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
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
     * <p><b>刻意不写 {@code is_deleted} 列</b>：内置 {@code insert} 对「值为 null 且非 fill 字段」的列
     * 会用 {@code <if>} 整列省略，让 {@code NOT NULL DEFAULT 0} 生效；本 SQL 若显式绑定 null，
     * 严格模式下会直接报 {@code 1048 Column 'is_deleted' cannot be null}。此处与内置口径保持一致。</p>
     *
     * <p>注意 MyBatis-Plus 的参数处理按<b>集合值相等</b>去重（{@code HashSet.add(value)}）：
     * 同一方法里若出现两个内容相等的集合参数，第二个会被静默跳过。</p>
     *
     * @param rows 待插入用户（调用方保证非空）
     * @return 影响行数
     */
    @Insert("<script>"
        + "INSERT INTO sys_user"
        + " (id, tenant_id, username, user_type, password, real_name, nickname, dept_id, avatar, phone,"
        + "  email, gender, remark, last_login_time, pwd_reset_time, create_user, create_time,"
        + "  update_user, update_time, status) VALUES "
        + "<foreach collection='rows' item='r' separator=','>"
        + " (#{r.id}, #{r.tenantId}, #{r.username}, #{r.userType}, #{r.password}, #{r.realName}, #{r.nickname},"
        + "  #{r.deptId}, #{r.avatar}, #{r.phone}, #{r.email}, #{r.gender}, #{r.remark}, #{r.lastLoginTime},"
        + "  #{r.pwdResetTime}, #{r.createUser}, #{r.createTime}, #{r.updateUser}, #{r.updateTime},"
        + "  #{r.status})"
        + "</foreach>"
        + "</script>")
    int insertBatch(@Param("rows") List<SysUser> rows);

    /**
     * 全局统计同用户名用户数（跨部门、跨租户），供用户名唯一性校验使用。
     *
     * <p><b>为什么必须绕过两道过滤</b>：{@code sys_user} 的唯一键是
     * {@code UNIQUE KEY uk_username (username)}——<b>不带 tenant_id 的全局唯一键</b>；而新增/修改用户
     * 所在的调用链处于数据权限与租户过滤作用域内（方法上的 {@code @DataPermission} + 租户行拦截器）。
     * 若用内置 {@code exists}/{@code selectCount} 查重，本部门之外（乃至其它租户）的重名用户查不到，
     * 校验会「通过」，最后由数据库唯一键抛原始 SQL 错误——用户看到的是 SQL 报错而不是业务提示。</p>
     *
     * <p>两道过滤的关闭方式不同，缺一不可：</p>
     * <ol>
     *   <li><b>租户过滤</b>：由调用侧 {@code TenantContext.executeIgnore} 关闭（本仓既有写法，
     *       参照 {@code UserAccountSupport#checkPhoneUnique}），不在本方法内声明。</li>
     *   <li><b>数据权限</b>：属 MyBatis-Plus 拦截器级行为，{@code TenantContext} 管不到——
     *       {@code DataPermissionContext} 只有进入/退出、没有「挂起」语义，且外层方法已激活上下文时，
     *       内层方法上的 {@code @DataPermission(ignore = true)} 并不会让上下文失效（它只是自己不再进入）。
     *       故这里用 {@code @InterceptorIgnore(dataPermission = "true")} 让本语句跳过数据权限拦截器：
     *       MyBatis-Plus 3.5.17 的 {@code DataPermissionInterceptor#beforeQuery} 与 {@code #beforePrepare}
     *       首行即判 {@code InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())}（已由字节码核实，
     *       并由 {@code SysUserMapperInterceptorIgnoreTest} 断言该判据对本语句为真）。</li>
     * </ol>
     *
     * <p><b>影响范围</b>：{@code @InterceptorIgnore} 只作用于本语句，不改变任何其它查询的数据范围；
     * 本方法只返回行数，不返回任何用户数据，故不构成越权读取。</p>
     *
     * @param username  用户名
     * @param excludeId 需排除的用户 ID（编辑时传自身 ID，新增传 {@code null}）
     * @return 匹配行数（{@code > 0} 即重名）
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>"
        + "SELECT COUNT(1) FROM sys_user WHERE username = #{username} AND is_deleted = 0"
        + "<if test='excludeId != null'> AND id != #{excludeId}</if>"
        + "</script>")
    long countByUsernameGlobal(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * 全局统计同手机号用户数（跨部门、跨租户），供手机号唯一性校验使用。
     *
     * <p><b>为什么与用户名查重是同一类问题</b>：{@code sys_user} 的手机号同样按
     * <b>不带 tenant_id 的全局唯一</b>语义约束（删号时置空 {@code phone} 以释放占用，
     * 见 {@code SysUserServiceImpl#deleteUser}），而新增/修改用户所在的调用链处于数据权限与
     * 租户过滤作用域内（{@code updateUser} 方法上的 {@code @DataPermission} + 租户行拦截器）。
     * 若用内置 {@code exists}/{@code selectCount} 查重，本部门之外的重号用户查不到，校验会「通过」，
     * 最后由唯一键抛原始 SQL 错误——用户看到的是 SQL 报错而不是业务提示。</p>
     *
     * <p>两道过滤的关闭方式与 {@link #countByUsernameGlobal} 完全一致，缺一不可：</p>
     * <ol>
     *   <li><b>租户过滤</b>：由调用侧 {@code TenantContext.executeIgnore} 关闭（本仓既有写法），
     *       不在本方法内声明。</li>
     *   <li><b>数据权限</b>：属 MyBatis-Plus 拦截器级行为，{@code TenantContext} 管不到——
     *       {@code DataPermissionContext} 只有进入/退出、没有「挂起」语义，且外层方法已激活上下文时，
     *       内层方法上的 {@code @DataPermission(ignore = true)} 并不会让上下文失效（它只是自己不再进入）。
     *       故这里用 {@code @InterceptorIgnore(dataPermission = "true")} 让本语句跳过数据权限拦截器。</li>
     * </ol>
     *
     * <p><b>影响范围</b>：{@code @InterceptorIgnore} 只作用于本语句，不改变任何其它查询的数据范围；
     * 本方法只返回行数，不返回任何用户数据，故不构成越权读取。</p>
     *
     * @param phone     手机号
     * @param excludeId 需排除的用户 ID（编辑时传自身 ID，新增传 {@code null}）
     * @return 匹配行数（{@code > 0} 即重号）
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>"
        + "SELECT COUNT(1) FROM sys_user WHERE phone = #{phone} AND is_deleted = 0"
        + "<if test='excludeId != null'> AND id != #{excludeId}</if>"
        + "</script>")
    long countByPhoneGlobal(@Param("phone") String phone, @Param("excludeId") Long excludeId);

}
