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
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间（楼栋/楼层/房间/办公室/仓库/宿舍等）树形主数据。
 *
 * <p><b>为什么在基础模块而不是 IoT</b>：空间是组织与资产的<b>通用定位维度</b>，不是某个业务域的私产。
 * 部门/公司/人员/资产/设备都要挂到空间上；IoT 设备只是引用方之一。放在基础模块才能保证
 * 依赖方向单向（iot → system-api），不会出现「基础模块反向依赖 IoT 契约」。</p>
 *
 * <p><b>与 {@code iot_twin_scene} 的边界（硬约束）</b>：孪生场景是 <b>3D 展示层</b>，不是空间主数据。
 * 同一个「A 栋 1F」在业务上只有一条空间记录；未来孪生场景通过 {@code space_id} 引用它，
 * 场景只保留模型/锚点/视角。两者不得各自维护一份「楼栋/楼层/房间」。</p>
 *
 * <p><b>{@code level}（层级深度）由服务端计算，不取信入参</b>：它参与删除守卫与树深上限判定，
 * 若允许客户端传，就能绕过深度限制。</p>
 *
 * <p><b>{@code extJson} 是扩展位</b>：业务自定义属性（消防分区、空调数量、资产编号前缀等）走 JSON，
 * 后端只校验合法性与大小上限。不把未来可能的字段提前建成列——那会让表结构随每个业务域膨胀。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
@TableName("sys_space")
public class SysSpace extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父空间 ID，顶级为 0 */
    private Long pid;

    /** 空间名称 */
    private String name;

    /**
     * 空间编码（租户内唯一；为空表示不参与唯一约束，供业务侧按编码引用）。
     *
     * <p>{@code updateStrategy = ALWAYS}：允许用户把编码<b>清空</b>。MyBatis-Plus 默认
     * {@code NOT_NULL} 策略下 {@code updateById} 会跳过值为 null 的字段 ⇒ 前端清空编码后
     * 提示成功、库里仍保留旧值（静默失败），「想释放编码给别人用」永远做不到。
     * 本类所有可空业务字段都标注该策略，原因相同。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String code;

    /** 空间类型（字典 {@code space_type} 的项值，如 building/floor/office/warehouse/dormitory） */
    private String spaceType;

    /** 层级深度（根为 1，服务端维护） */
    private Integer level;

    /** 同级排序 */
    private Integer sort;

    /** 详细地址（园区/楼栋常用） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;

    /** 经度（WGS84） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal longitude;

    /** 纬度（WGS84） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal latitude;

    /** 所在楼层号（如 1、-1、1F） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String floorNo;

    /** 房间号（如 101） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String roomNo;

    /** 面积（平方米） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal area;

    /** 容纳人数/容量 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer capacity;

    /** 空间负责人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String manager;

    /** 联系电话 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;

    /** 备注 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 扩展属性 JSON（业务自定义字段，后端只校验合法性与大小） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extJson;
}
