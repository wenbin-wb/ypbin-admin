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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 空间 CSV 导入的结果。
 *
 * <h2>为什么不是「成功 / 失败」一个布尔</h2>
 *
 * 导入几百行时，用户需要知道**哪几行错了、错在哪**——只知道「导入失败」等于让他
 * 逐行猜。故本类逐行报错，且带行号（**CSV 里的物理行号**，含表头，用户可以在编辑器里
 * 直接跳到那一行）。
 *
 * <h2>为什么错误只返回前 N 条</h2>
 *
 * 一次导入 5000 行可能全错（比如整张表的编码格式都不对）。把 5000 条错误塞进响应会
 * 撑爆报文、前端也渲染不动。故截断到 {@code MAX_IMPORT_ERRORS} 条，
 * 并用 {@link #errorTruncated} 明确告知「还有更多」——
 * 静默截断会让用户以为只有这几个错。
 *
 * @author wenbin
 * @since 2026-10-27
 */
@Getter
@Setter
public class SpaceImportResultResp {

    /** 新增成功的行数 */
    private int inserted;

    /** 更新成功的行数（仅 upsert 模式可能非 0） */
    private int updated;

    /** 失败的行数（**全量计数**，不受错误明细截断影响） */
    private int failed;

    /**
     * 错误明细（最多 {@code MAX_IMPORT_ERRORS} 条）。
     *
     * <p>无错误时为空列表（**不返回 null**：前端可直接渲染空状态，不必判空）。</p>
     */
    private List<RowError> errors = new ArrayList<>();

    /**
     * 错误明细是否被截断（即还有未列出的错误）。
     *
     * <p>为 true 时前端必须提示「仅显示前 N 条」——否则用户会以为总共只错这么多。</p>
     */
    private boolean errorTruncated;

    /**
     * 单行错误。
     *
     * <p><b>{@code lineNo} 是 CSV 物理行号</b>（表头为第 1 行，第一条数据为第 2 行），
     * 与用户在 Excel/编辑器中看到的行号一致——不是「第几条数据」。
     * 这两者差 1（有表头时），写错会让用户去改错误的那一行。</p>
     */
    @Getter
    @Setter
    public static class RowError {

        /** CSV 物理行号（含表头，从 1 开始） */
        private int lineNo;

        /** 该行的空间编码（可能为空：编码列本身缺失时用来帮助定位） */
        private String code;

        /** 失败原因（面向用户，可直接展示） */
        private String message;

        /**
         * 构造单行错误。
         *
         * @param lineNo  CSV 物理行号
         * @param code    该行编码（可空）
         * @param message 失败原因
         */
        public RowError(int lineNo, String code, String message) {
            this.lineNo = lineNo;
            this.code = code;
            this.message = message;
        }

        /** 供序列化框架使用（Jackson 反序列化需要无参构造）。 */
        public RowError() {
        }
    }

    /**
     * 追加一条错误明细（超出上限时置 {@code errorTruncated}）。
     *
     * <p>把这个判断收进 DTO，避免调用方各自 `if (errors.size() < N)` ——
     * 那种散落写法迟早漏掉「截断标志」这一半。</p>
     *
     * @param lineNo  CSV 物理行号
     * @param code    该行编码（可空）
     * @param message 失败原因
     * @param maxKeep 明细上限
     */
    public void addError(int lineNo, String code, String message, int maxKeep) {
        this.failed++;
        if (errors.size() < maxKeep) {
            errors.add(new RowError(lineNo, code, message));
        } else {
            errorTruncated = true;
        }
    }
}
