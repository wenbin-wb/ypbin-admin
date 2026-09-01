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

/**
 * 用户账号状态。
 *
 * <p>数据库与接口存/传 {@code code}；业务判断用 {@code getCode()} 比较，展示文案统一取
 * {@link #descOf(Integer)}，禁止散落裸数字映射。</p>
 *
 * <p>共享类：复制自单体版 ypbin-admin-system（与 system-svc 同源），
 * 已归位至 api 模块，作为跨服务共享契约。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
public enum UserStatusEnum {

    /** 正常 */
    ENABLED(1, "正常"),

    /** 禁用 */
    DISABLED(0, "禁用");

    private final Integer code;
    private final String desc;

    UserStatusEnum(Integer code, String desc) {
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
     * 按编码取展示文案，未匹配（含 {@code null}）时归为未知。
     *
     * @param code 状态编码
     * @return 展示文案
     */
    public static String descOf(Integer code) {
        for (UserStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.desc;
            }
        }
        return "未知";
    }
}
