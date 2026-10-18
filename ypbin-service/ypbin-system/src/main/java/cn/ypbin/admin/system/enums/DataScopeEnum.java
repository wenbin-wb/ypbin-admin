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

import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ScopeType;
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
     * 按编码解析数据范围（<b>委托 starter 的 {@link ScopeType#fromCode}</b>，码值口径单一来源）。
     *
     * <p><strong>为什么保留本枚举</strong>：{@code sys_role.data_scope} 的 1–5 码值被 DDL 注释、前端字典与
     * 迁移脚本引用，属<b>对外数据契约</b>；上收的是「算法与码值的单一来源」，不是把枚举类型搬走。
     * 故 {@code fromCode} 的<b>判据</b>交给 starter（{@link ScopeType#fromCode}），本枚举只负责
     * 「范围类型 → 本仓枚举」的映射，避免两处各写一份码值表、漂移时静默错判。</p>
     *
     * <p>未匹配（含 {@code null} 与库中脏值）返回空。空值对调用方是「该角色不贡献任何数据范围」，
     * 属收窄而非放大，调用方须记日志暴露脏值而不是静默忽略。</p>
     *
     * @param code 数据范围编码
     * @return 数据范围枚举，未匹配时为空
     */
    public static Optional<DataScopeEnum> fromCode(Integer code) {
        ScopeType type = ScopeType.fromCode(code);
        if (type == ScopeType.UNKNOWN) {
            return Optional.empty();
        }
        return Optional.of(valueOf(type.name()));
    }

    /**
     * 本枚举对应的 starter 范围类型（供数据范围算法使用；码值解析已委托 starter）。
     *
     * @return starter 的范围类型
     */
    public ScopeType scopeType() {
        return ScopeType.fromCode(code);
    }
}
