/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysSpaceRelation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 空间关联 Mapper。
 *
 * <p><b>为什么需要一个自定义的 {@code physicalDelete}</b>：本表是<b>纯关联表</b>，关联行没有保留价值，
 * 删除必须是物理删。但实体继承 {@code TenantBaseEntity}，其 {@code isDeleted} 带
 * {@code @TableLogic} ⇒ 继承自 {@code BaseMapper} 的 {@code delete(...)} 会被改写成
 * {@code UPDATE ... SET is_deleted = 1}。而表上有唯一键
 * {@code uk_space_relation(tenant_id, space_id, target_type, target_id)}，
 * 逻辑删除后行仍在 ⇒ <b>解绑一次之后该组合永久无法再绑</b>（实测 1062 Duplicate entry）。</p>
 *
 * <p>这不是「关掉逻辑删除」这类全局取舍，而是本表的局部语义：把「关联行是否保留历史」判定为
 * <b>不保留</b>（与同仓 {@code sys_user_post}/{@code sys_role_menu} 等纯关联表一致），
 * 用一条显式 SQL 表达该判定。刻意不用 {@code @TableLogic} 的全局绕过开关，避免影响其它表。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
public interface SysSpaceRelationMapper extends BaseMapper<SysSpaceRelation> {

    /**
     * 物理删除满足条件的关联行（不经逻辑删除）。
     *
     * <p>条件用 {@link LambdaQueryWrapper} 表达而非手写 SQL：这样租户插件、字段名映射
     * 仍按框架口径生效，只是把 DELETE 的「逻辑删改写」这一步跳过。</p>
     *
     * @param wrapper 删除条件
     * @return 实际删除行数
     */
    @Delete("""
        <script>
        DELETE FROM sys_space_relation
        <where>
            ${ew.sqlSegment}
        </where>
        </script>
        """)
    int physicalDelete(@Param("ew") LambdaQueryWrapper<SysSpaceRelation> wrapper);
}
