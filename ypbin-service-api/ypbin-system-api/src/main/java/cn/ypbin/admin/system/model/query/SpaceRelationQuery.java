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
package cn.ypbin.admin.system.model.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 空间关联反查条件（对象 → 空间）。
 *
 * <p>不分页：一个业务对象关联的空间数在业务上是个位数（一个部门几个办公点、一台设备一个位置），
 * 强制分页反而让调用方必须处理分页信封。列表页若真要限流，由 {@code SpaceConstants} 的批量上限兜住。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SpaceRelationQuery {

    /** 关联对象类型（必填：不知道类型就无法定位 target_id 的含义） */
    private String targetType;

    /** 关联对象 ID（必填） */
    private Long targetId;

    /** 只查主空间（true=只返回 is_primary=1 的那条） */
    private Boolean primaryOnly;
}
