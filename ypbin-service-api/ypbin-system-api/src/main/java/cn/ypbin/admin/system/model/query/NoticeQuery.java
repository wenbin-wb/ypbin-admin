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
 * 公告分页查询条件。
 *
 * <p>列表不再一次性返回全部公告与正文（MEDIUMTEXT），正文由详情接口单独读取。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class NoticeQuery extends PageQuery {

    /** 标题关键字 */
    private String title;

    /** 公告类型 */
    private Integer noticeType;

    /** 发布状态：0 草稿、1 已发布、2 已撤回 */
    private Integer publishStatus;
}
