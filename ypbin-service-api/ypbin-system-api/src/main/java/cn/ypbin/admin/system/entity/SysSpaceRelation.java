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
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间与业务对象的关联（多态）。
 *
 * <p><b>为什么用一张多态关联表，而不是每个业务域一张关联表</b>：部门挂空间、人员挂空间、设备挂空间、
 * 未来资产/车辆/工单挂空间，关联结构完全相同（空间 ID + 对象类型 + 对象 ID + 是否主空间）。
 * 每个业务域各建一张表会产生 N 份同构 DDL 与 N 份增删改查；用 {@code target_type} 区分后，
 * 新增业务域只需在字典 {@code space_target_type} 加一项 + 业务侧自行校验目标是否存在，空间侧零改动。</p>
 *
 * <p><b>代价如实说明</b>：多态关联无法建数据库外键，目标对象被删除时不会级联清理。
 * 因此：① 业务侧删除对象时<b>必须</b>调空间服务解绑（或由空间侧提供按目标删除的清理方法）；
 * ② 反查目标名称需要我方按类型分发（见 {@code SpaceTargetNameResolver}）。这是有意取舍——
 * 换来的是一张表覆盖全部业务域，而不是每加一个业务就加一张表。</p>
 *
 * <p><b>{@code isPrimary}</b>：一个对象可关联多个空间（如市场部在 A 楼 1F 与 B 楼 3F 都有办公点），
 * 但「主空间」最多一个，由服务层保证（数据库唯一键无法表达条件唯一）。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
@TableName("sys_space_relation")
public class SysSpaceRelation extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 空间 ID */
    private Long spaceId;

    /** 关联对象类型（字典 {@code space_target_type} 的项值，如 dept/user/device/asset） */
    private String targetType;

    /** 关联对象 ID（含义由 targetType 决定） */
    private Long targetId;

    /** 是否主空间：1 是 0 否（每个对象最多一个主空间，服务层保证） */
    private Integer isPrimary;

    /** 备注（如「主要办公点」「备用仓」） */
    private String remark;
}
