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
import cn.ypbin.starter.json.ref.RefText;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间树节点（用于 {@code /system/space/tree}）。
 *
 * <p>叶子节点的 {@code children} 保持 {@code null} 而不是空列表：本类带
 * {@link JsonInclude.Include#NON_NULL}，但该注解<b>不过滤空集合</b>——
 * 若一律写回空列表，叶子会多出 {@code "children":[]}，与部门接口的既有响应形态不一致。
 * 故服务层显式把叶子的空 children 归一为 null（与 {@code SysDeptServiceImpl#tree} 同口径）。</p>
 *
 * <p>{@code spaceTypeName} 由字典翻译：字典项被改名后这里自动跟随，不在表中冗余存中文名。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpaceResp {

    private Long id;

    private Long pid;

    private String name;

    private String code;

    /**
     * 空间类型（字典 {@code space_type} 的项值）。
     *
     * <p>序列化时额外输出 {@code spaceTypeText}（starter 的 {@code @DictText} 机制，
     * 与 {@code UserResp.gender/status} 同一套做法）。<b>不在服务层手工查字典翻译</b>——
     * 那会与 starter 的翻译口径形成两份实现，字典项改名时一处生效一处不生效。</p>
     */
    @DictText("space_type")
    private String spaceType;

    /** 层级深度（根为 1） */
    private Integer level;

    private Integer sort;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String floorNo;

    private String roomNo;

    private BigDecimal area;

    private Integer capacity;

    private String manager;

    private String phone;

    private Integer status;

    private String remark;

    private String extJson;

    /** 空间完整路径（如「总部园区 / A栋 / 1F / 101室」；由服务端按父链拼接） */
    private String spacePath;

    /** 该空间下的直接子空间数（列表页展示用；无子空间时为 0） */
    private Long childCount;

    /** 该空间上的关联对象数（部门/人员/设备等合计） */
    private Long relationCount;

    @RefText("user")
    private Long createUser;

    private LocalDateTime createTime;

    /** 子节点（叶子为 null，不写空列表） */
    private List<SpaceResp> children;
}
