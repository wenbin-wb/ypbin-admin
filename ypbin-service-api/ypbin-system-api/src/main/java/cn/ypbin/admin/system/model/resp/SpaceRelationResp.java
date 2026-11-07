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
package cn.ypbin.admin.system.model.resp;

import cn.ypbin.starter.json.dict.DictText;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间关联响应（某个业务对象挂在哪个空间上）。
 *
 * <p>反查方向（对象 → 空间）是业务侧最常用的：部门详情页要显示「本部门在哪些空间」、
 * 设备详情要显示「本设备在哪个位置」。因此本响应把空间侧的关键信息一并带出，
 * 避免前端再逐条查空间。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SpaceRelationResp {

    private Long id;

    private Long spaceId;

    /** 空间名称（联查得到） */
    private String spaceName;

    /** 空间编码 */
    private String spaceCode;

    /** 空间类型（字典 space_type 项值）；序列化时额外输出 {@code spaceTypeText} */
    @DictText("space_type")
    private String spaceType;

    /** 空间完整路径（如「总部园区 / A 栋 / 1F / 101 室」，由父链拼接） */
    private String spacePath;

    private String targetType;

    private Long targetId;

    private Integer isPrimary;

    private String remark;

    private Integer status;

    private LocalDateTime createTime;
}
