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
package cn.ypbin.admin.system.model.dto;

import cn.ypbin.starter.json.dict.DictText;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间关联摘要（跨服务只读视图）：某个业务对象挂在哪个空间上。
 *
 * <p>供 IoT 等业务服务反查「这台设备在哪个空间」，只读、不含审计字段。
 * 写入方向（把设备挂到空间）由调用方经 {@code ISystemClient.bindSpaceRelations} 完成，
 * 调用方不得直接写 {@code sys_space_relation}（跨服务禁止访问对方表）。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SysSpaceRelationDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联行 ID */
    private Long id;

    /** 空间 ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 空间编码 */
    private String spaceCode;

    /** 空间类型（字典 space_type 项值）；序列化时额外输出 {@code spaceTypeText} */
    @DictText("space_type")
    private String spaceType;

    /** 空间完整路径 */
    private String spacePath;

    /** 关联对象类型（dept/user/device/asset…） */
    private String targetType;

    /** 关联对象 ID */
    private Long targetId;

    /** 是否主空间：1 是 0 否 */
    private Integer isPrimary;
}
