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
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.support.AbstractDataScopeResolver;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 数据范围处理器（starter {@code DataScopeHandler} 端口的宿主适配）。
 *
 * <p><strong>本类只承担端口绑定</strong>：把 starter 端口的签名
 * {@code getDataScopeSql(mappedStatementId, tableName)} 接到 {@code service/support} 层的数据范围
 * 解析能力上。规则本身、递归标记、fail-closed 语义与 SQL 片段文本全部由
 * {@link AbstractDataScopeResolver} 实现——读写两条路径（端口回调与服务层写路径校验）
 * 因此共用<strong>唯一一份</strong>口径，不会漂移。</p>
 *
 * <p><strong>依赖方向</strong>：{@code provider → service/support}。业务服务
 * （如 {@code SysUserServiceImpl}）只依赖 {@code service/support} 的数据范围解析接口，
 * 不再依赖本类；本类是该能力的唯一 Spring Bean（{@code @Component} 装配），
 * 端口链与业务服务都注入到同一个实例，递归标记也只可能有一份。</p>
 *
 * <p>端口对宿主的期望、治理表白名单与已知边界，见 {@link AbstractDataScopeResolver} 的类注释。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@Component
public class AdminDataScopeHandler extends AbstractDataScopeResolver implements DataScopeHandler {

    public AdminDataScopeHandler(ObjectProvider<SysRoleMapper> roleMapperProvider,
        ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider,
        ObjectProvider<SysDeptMapper> deptMapperProvider,
        ObjectProvider<SysPermissionService> permissionServiceProvider) {
        super(roleMapperProvider, roleDeptMapperProvider, deptMapperProvider, permissionServiceProvider);
    }

    @Override
    public String getDataScopeSql(String mappedStatementId, String tableName) {
        return resolveCondition(mappedStatementId, tableName);
    }
}
