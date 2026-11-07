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
 * 空间摘要（跨服务只读视图）。
 *
 * <p><b>为什么是 DTO 而不是实体</b>：{@code system-api} 是跨服务契约模块，暴露持久化实体等于把
 * MyBatis 拉给调用方（本仓已有架构门禁禁止契约模块引用 {@code system.entity} 包）。
 * 本类只承载「调用方渲染空间位置」所需的最小字段集：id / 名称 / 类型 / 层级 / 完整路径。</p>
 *
 * <p><b>为什么带 {@code spacePath}</b>：调用方（如 IoT 设备列表）要显示「A栋 1F 101室」，
 * 若只给 {@code pid}，每个调用方都要自己拉整棵树再拼路径——那是 N 次 RPC 与 N 份拼装逻辑。
 * 由 system 侧一次算好，调用方直接展示。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SysSpaceDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 空间 ID */
    private Long id;

    /** 父空间 ID（0=顶级） */
    private Long pid;

    /** 空间名称 */
    private String name;

    /** 空间编码（可空） */
    private String code;

    /** 空间类型（字典 space_type 项值）；序列化时额外输出 {@code spaceTypeText}（@DictText） */
    @DictText("space_type")
    private String spaceType;

    /** 层级深度（根为 1） */
    private Integer level;

    /** 完整路径（如「总部园区 / A栋 / 1F / 101室」） */
    private String spacePath;

    /** 状态：1 正常 0 停用 */
    private Integer status;
}
