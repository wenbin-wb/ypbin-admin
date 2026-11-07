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

import cn.ypbin.starter.crud.model.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间列表查询条件（平铺分页，供「搜索/筛选」用；树形展示走 {@code /tree}）。
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SpaceQuery extends PageQuery {

    /** 名称/编码模糊匹配 */
    private String keyword;

    /** 空间类型过滤（字典 space_type 项值） */
    private String spaceType;

    /** 父空间 ID 过滤（null=不过滤；传 0 只查顶级） */
    private Long pid;

    /** 状态过滤（null=不过滤） */
    private Integer status;
}
