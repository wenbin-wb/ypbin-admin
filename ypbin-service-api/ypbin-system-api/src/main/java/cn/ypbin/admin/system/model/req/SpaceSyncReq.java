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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 按「业务对象」同步其空间集合的请求：把某对象关联的空间**整体替换**为给定集合。
 *
 * <p><b>为什么需要它（而不是让前端自己算差集逐个 bind/unbind）</b>：部门表单这类场景的语义是
 * 「这个部门现在关联这几个空间」——用户改一次表单就要「删掉不要的、加上新选的」。
 * 若由前端算差集，会退化成 N 次 HTTP 请求（每个空间一次），且**没有事务**：
 * 第 3 个请求失败时前 2 个已落库，页面显示「保存失败」但库已是半改状态。
 * 本端点把「算差集 + 批量增删」放进**一个事务**，要么全成要么全不成。</p>
 *
 * <p><b>不接收 {@code isPrimary}</b>：整体替换语义下「主空间」需要单独决策（哪一个设为主），
 * 由调用方在同步后按需调 {@code /space/{id}/relations} 显式设置。把它塞进本请求会让
 * 「用户只是想改空间集合」意外改掉主空间归属。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SpaceSyncReq {

    /** 关联对象类型（字典 space_target_type 项值） */
    @NotBlank(message = "关联对象类型不能为空")
    @Size(max = SpaceConstants.MAX_TARGET_TYPE_LENGTH, message = "关联对象类型最长 32 字符")
    private String targetType;

    /** 关联对象 ID */
    @NotNull(message = "关联对象 ID 不能为空")
    private Long targetId;

    /**
     * 目标空间集合（**全量**：不在列表里的既有空间关联会被删除）。
     *
     * <p>允许为<b>空列表</b>语义 = 清空该对象的全部空间关联；
     * 但不允许为 {@code null}（那等同于「调用方忘了传」，会静默清空，属危险默认值）。</p>
     */
    @NotNull(message = "目标空间集合不能为空（清空请传空数组）")
    @Size(max = SpaceConstants.MAX_BATCH_TARGETS, message = "单次最多关联 500 个空间")
    private List<Long> spaceIds;
}
