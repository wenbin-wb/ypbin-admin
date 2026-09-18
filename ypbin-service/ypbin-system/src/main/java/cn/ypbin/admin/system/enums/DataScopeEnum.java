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
package cn.ypbin.admin.system.enums;

import java.util.Optional;

/**
 * 角色数据范围。
 *
 * <p>取值与 {@code sys_role.data_scope} 列注释一致（1 全部、2 本部门及以下、3 本部门、4 仅本人、
 * 5 自定义），数据库存 {@code code}；判定一律走本枚举，禁止在业务代码里散落裸数字。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public enum DataScopeEnum {

    /** 全部数据（租户内不受部门约束） */
    ALL(1, "全部数据"),

    /** 本部门及以下数据 */
    DEPT_AND_CHILD(2, "本部门及以下数据"),

    /** 本部门数据 */
    DEPT(3, "本部门数据"),

    /** 仅本人数据 */
    SELF(4, "仅本人数据"),

    /** 自定义数据（部门取自角色-部门关联） */
    CUSTOM(5, "自定义数据");

    private final Integer code;
    private final String desc;

    DataScopeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按编码解析数据范围。
     *
     * <p>未匹配（含 {@code null} 与库中脏值）返回空。空值对调用方是「该角色不贡献任何数据范围」，
     * 属收窄而非放大，调用方须记日志暴露脏值而不是静默忽略。</p>
     *
     * @param code 数据范围编码
     * @return 数据范围枚举，未匹配时为空
     */
    public static Optional<DataScopeEnum> fromCode(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        for (DataScopeEnum item : values()) {
            if (item.code.equals(code)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
