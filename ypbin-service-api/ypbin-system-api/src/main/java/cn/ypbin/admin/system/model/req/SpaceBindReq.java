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
package cn.ypbin.admin.system.model.req;

import cn.ypbin.admin.system.constant.SpaceConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间绑定请求：把一批业务对象（部门/人员/设备…）挂到某个空间上。
 *
 * <p><b>为什么是批量</b>：业务上的真实动作是「把这 20 台设备挪到 3F 机房」，
 * 逐条调用会让前端发 20 次请求、每次一个事务；批量接口一次校验一次提交。</p>
 *
 * <p><b>不校验目标是否存在</b>：多态关联无法在空间侧判断 {@code dept/user/device} 哪张表，
 * 目标存在性由<b>各业务域自身</b>保证（IoT 绑设备时由 IoT 校验设备存在）。
 * 空间侧只保证「空间存在 + 空间未被删除 + 空间属于本租户」。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SpaceBindReq {

    /** 关联对象类型（字典 space_target_type 的项值，如 dept/user/device） */
    @NotBlank(message = "关联对象类型不能为空")
    @Size(max = SpaceConstants.MAX_TARGET_TYPE_LENGTH, message = "关联对象类型最长 32 字符")
    private String targetType;

    /** 关联对象 ID 列表（至少一个） */
    @NotEmpty(message = "请至少选择一个关联对象")
    @Size(max = SpaceConstants.MAX_BATCH_TARGETS, message = "单次最多绑定 500 个对象")
    private List<Long> targetIds;

    /** 是否设为主空间：1 是 0 否（选填，默认 0） */
    @Min(value = 0, message = "主空间标记只能为 0 或 1")
    @Max(value = 1, message = "主空间标记只能为 0 或 1")
    private Integer isPrimary;

    /** 关联备注 */
    @Size(max = SpaceConstants.MAX_RELATION_REMARK_LENGTH, message = "关联备注最长 255 字")
    private String remark;

    /**
     * 是否先清空这些对象在其它空间下的同类关联（默认 {@code false}）。
     *
     * <p>典型场景：把一台设备从 A 楼挪到 B 楼时置 {@code true}，否则设备会同时出现在两个空间。
     * 部门/人员则通常置 {@code false}（一个部门可对应多个空间点）。</p>
     */
    private Boolean clearOthers;
}
