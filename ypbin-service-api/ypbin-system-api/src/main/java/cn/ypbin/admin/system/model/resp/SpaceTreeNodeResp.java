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
import lombok.Getter;
import lombok.Setter;

/**
 * 空间树**懒加载**节点（用于 {@code GET /space/tree/lazy}）。
 *
 * <p><b>为什么另立一个 DTO 而不是复用 {@link SpaceResp}</b>：两者用途不同，字段集必须不同——</p>
 * <ul>
 *   <li>{@link SpaceResp} 是「一行完整数据」：25 个字段（地址/经纬度/面积/容量/负责人/扩展 JSON…），
 *       供详情与编辑回填；</li>
 *   <li>本类是「树上的一个可点击节点」：只带渲染树所需的最小信息 + {@code hasChildren}
 *       （决定是否显示展开箭头）。若复用 {@code SpaceResp}，一万个节点会带上几 MB 的
 *       地址/JSON/负责人等**当前根本用不到**的字段，而树是每次进页面必拉的接口。</li>
 * </ul>
 *
 * <p><b>关键字段 {@code hasChildren}</b>：懒加载树必须能提前知道「这个节点要显示展开箭头吗」。
 * 若不带它，前端只能先渲染成叶子、用户点开后才知道有没有子节点——箭头闪烁/缺失，
 * 交互上表现为「点了才知道有下一层」。用一次分组查询算出，不额外 per-node 查询。</p>
 *
 * @author wenbin
 * @since 2026-10-25
 */
@Getter
@Setter
public class SpaceTreeNodeResp {

    private Long id;

    private Long pid;

    private String name;

    private String code;

    /** 空间类型（字典 {@code space_type} 项值）；序列化时额外输出 {@code spaceTypeText} */
    @DictText("space_type")
    private String spaceType;

    /** 同级排序 */
    private Integer sort;

    /** 状态：1 正常 0 停用（停用节点在树上置灰，但仍展示——否则已停用空间上的关联会「消失」） */
    private Integer status;

    /** 是否有直接子空间（决定树是否显示展开箭头） */
    private Boolean hasChildren;
}
