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
package cn.ypbin.admin.system.constant;

/**
 * 空间域常量与上限（<b>唯一来源</b>）。
 *
 * <p>同一个上限会同时出现在请求 DTO 的 {@code @Size}、服务侧复核、前端常量三处，散落必然漂移；
 * 漂移的后果是「前端放过、后端拒绝」或反之，用户拿到的是无法理解的失败。故集中于此。</p>
 *
 * <p><b>为什么放在 api 模块</b>：请求 DTO（{@code SpaceSaveReq}）在 api 模块、服务实现在 service 模块，
 * 两侧都要引用同一组上限。放 service 模块会让 api 反向依赖它（api 是被依赖方）；放 common 会让
 * common 承载单个业务域常量。故随本域契约一起放 api。</p>
 *
 * <p><b>为什么深度上限是 6 而不是与孪生场景的 4 一致</b>：业务上典型最深路径是
 * 「园区 → 楼栋 → 楼层 → 房间 → 工位/货架」5 层，留一层余量；而孪生场景限 4 层是因为
 * <b>3D 模型加载成本</b>（每层一个模型），两者约束来源不同，不应强行对齐。空间树只存元数据，
 * 深度成本是字符串长度而非显存。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
public final class SpaceConstants {

    /** 顶级空间的父 ID（与菜单/部门的根约定一致） */
    public static final Long ROOT_PARENT_ID = 0L;

    /** 空间树最大深度（根为第 1 层）。超过即拒绝，避免页面递归渲染与父链查询失控 */
    public static final int MAX_SPACE_DEPTH = 6;

    /** 父链递归查询的深度余量：让「成环」与「超深」都能被业务侧识别，而不是撞 MySQL 递归上限 */
    public static final int PARENT_CHAIN_MARGIN = 2;

    /** 空间名称长度上限 */
    public static final int MAX_NAME_LENGTH = 128;

    /** 空间编码长度上限 */
    public static final int MAX_CODE_LENGTH = 64;

    /** 空间编码允许的字符集：字母数字下划线连字符（供业务侧按编码稳定引用） */
    public static final String CODE_PATTERN = "^[A-Za-z0-9_-]{1,64}$";

    /** 空间类型长度上限 */
    public static final int MAX_SPACE_TYPE_LENGTH = 32;

    /** 关联对象类型长度上限 */
    public static final int MAX_TARGET_TYPE_LENGTH = 32;

    /** 地址长度上限 */
    public static final int MAX_ADDRESS_LENGTH = 255;

    /** 楼层号长度上限 */
    public static final int MAX_FLOOR_NO_LENGTH = 16;

    /** 房间号长度上限 */
    public static final int MAX_ROOM_NO_LENGTH = 32;

    /** 负责人长度上限 */
    public static final int MAX_MANAGER_LENGTH = 64;

    /** 联系电话长度上限 */
    public static final int MAX_PHONE_LENGTH = 20;

    /** 备注长度上限 */
    public static final int MAX_REMARK_LENGTH = 500;

    /** 关联备注长度上限 */
    public static final int MAX_RELATION_REMARK_LENGTH = 255;

    // ---------- CSV 导入 / 导出 ----------

    /**
     * 导入时「父空间编码」列的列名。
     *
     * <p><b>为什么用编码而不是父空间 ID</b>：用户手上的 Excel/CSV 是**离线准备**的，
     * 里面不可能有本系统的自增 ID。编码是用户自己起的、可读且租户内唯一，
     * 是唯一能在离线表格里稳定表达「这一行挂在哪个上级下面」的字段。</p>
     *
     * <p>根节点的父编码留空。</p>
     */
    public static final String IMPORT_COLUMN_PARENT_CODE = "parentCode";

    /** 导入单次最大行数（与 {@code CsvLimits} 默认 1 万行一致，按空间场景收窄以控制事务时长）。 */
    public static final int MAX_IMPORT_ROWS = 5000;

    /** 导入错误明细最多返回前 N 条（避免一次返回上万条把响应撑爆，前端也渲染不动）。 */
    public static final int MAX_IMPORT_ERRORS = 100;

    /**
     * 导入模式：仅新增（已存在的编码报错）。
     *
     * <p>默认模式。<b>刻意不做「存在就更新」</b>：用户手里的表格往往只是一部分空间，
     * 静默覆盖会把表格里没写的字段清空（本表多数字段是 {@code updateStrategy=ALWAYS}）。</p>
     */
    public static final String IMPORT_MODE_INSERT = "insert";

    /**
     * 导入模式：存在则更新（按编码匹配）。
     *
     * <p>需调用方<b>显式指定</b>——它是有覆盖语义的危险操作。</p>
     */
    public static final String IMPORT_MODE_UPSERT = "upsert";

    /** 扩展属性 JSON 字节上限（16 KB）：防「把整份配置塞进扩展字段」 */
    public static final int MAX_EXT_JSON_BYTES = 16 * 1024;

    /** 单次批量绑定/解绑的对象数量上限（防一次请求打满事务） */
    public static final int MAX_BATCH_TARGETS = 500;

    /** 主空间标记：是 */
    public static final int PRIMARY_YES = 1;

    /** 主空间标记：否 */
    public static final int PRIMARY_NO = 0;

    private SpaceConstants() {
    }
}
