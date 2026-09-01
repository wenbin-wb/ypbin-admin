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
 * AI 知识库文档向量化处理状态。
 *
 * <p>注意区别于实体通用启用/禁用状态（{@code EntityStatus}）：本枚举描述文档的
 * 向量化生命周期。数据库与接口存/传 {@code code}，禁止散落裸数字映射。</p>
 *
 * <p>共享类：复制自单体版 ypbin-admin-system（与 system-svc 同源），
 * 后续抽公共库时统一收敛到 common-api，勿单侧修改。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
public enum AiDocumentStatusEnum {

    /** 处理中（待向量化或向量化进行时） */
    PROCESSING(0, "处理中"),

    /** 就绪（向量化完成，可参与检索） */
    READY(1, "就绪"),

    /** 失败 */
    FAILED(2, "失败");

    private final Integer code;
    private final String desc;

    AiDocumentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
