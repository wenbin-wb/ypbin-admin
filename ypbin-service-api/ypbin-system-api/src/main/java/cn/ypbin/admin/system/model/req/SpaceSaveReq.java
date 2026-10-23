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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间新增/编辑请求。
 *
 * <p><b>不接收 {@code level}</b>：层级深度由服务端按父链计算，客户端传了也会被忽略——
 * 若允许外部指定，就能绕过树深上限。{@code pid} 由服务端校验存在性与成环。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Getter
@Setter
public class SpaceSaveReq {

    /** 父空间 ID（null 或 0 表示顶级） */
    private Long pid;

    /** 空间名称 */
    @NotBlank(message = "空间名称不能为空")
    @Size(max = SpaceConstants.MAX_NAME_LENGTH, message = "空间名称最长 128 字")
    private String name;

    /** 空间编码（可空；填了必须符合字符集且租户内唯一） */
    @Size(max = SpaceConstants.MAX_CODE_LENGTH, message = "空间编码最长 64 字符")
    @Pattern(regexp = SpaceConstants.CODE_PATTERN,
        message = "空间编码只能包含字母、数字、下划线与连字符")
    private String code;

    /** 空间类型（字典 space_type 的项值） */
    @NotBlank(message = "空间类型不能为空")
    @Size(max = SpaceConstants.MAX_SPACE_TYPE_LENGTH, message = "空间类型最长 32 字符")
    private String spaceType;

    /** 同级排序 */
    private Integer sort;

    /** 详细地址 */
    @Size(max = SpaceConstants.MAX_ADDRESS_LENGTH, message = "地址最长 255 字")
    private String address;

    /** 经度（WGS84，-180 ~ 180） */
    @DecimalMin(value = "-180.0", message = "经度范围 -180 ~ 180")
    @DecimalMax(value = "180.0", message = "经度范围 -180 ~ 180")
    private BigDecimal longitude;

    /** 纬度（WGS84，-90 ~ 90） */
    @DecimalMin(value = "-90.0", message = "纬度范围 -90 ~ 90")
    @DecimalMax(value = "90.0", message = "纬度范围 -90 ~ 90")
    private BigDecimal latitude;

    /** 楼层号（如 1、-1、1F） */
    @Size(max = SpaceConstants.MAX_FLOOR_NO_LENGTH, message = "楼层号最长 16 字符")
    private String floorNo;

    /** 房间号 */
    @Size(max = SpaceConstants.MAX_ROOM_NO_LENGTH, message = "房间号最长 32 字符")
    private String roomNo;

    /** 面积（平方米），不得为负 */
    @DecimalMin(value = "0.0", message = "面积不能为负")
    private BigDecimal area;

    /** 容纳人数/容量，不得为负 */
    @PositiveOrZero(message = "容量不能为负")
    private Integer capacity;

    /** 空间负责人 */
    @Size(max = SpaceConstants.MAX_MANAGER_LENGTH, message = "负责人最长 64 字")
    private String manager;

    /** 联系电话 */
    @Size(max = SpaceConstants.MAX_PHONE_LENGTH, message = "联系电话最长 20 字")
    private String phone;

    /** 状态：1 正常 0 停用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;

    /** 备注 */
    @Size(max = SpaceConstants.MAX_REMARK_LENGTH, message = "备注最长 500 字")
    private String remark;

    /** 扩展属性 JSON（业务自定义字段；服务端只校验合法性及 16KB 大小上限） */
    private String extJson;
}
