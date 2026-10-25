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
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.model.resp.SpaceTreeNodeResp;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 空间 Mapper。
 *
 * <p><b>⚠️ 本 Mapper 的两个递归 CTE 语句必须显式跳过租户插件</b>（{@code @InterceptorIgnore(tenantLine = "true")}）
 * 并在 SQL 里<b>自己写租户条件</b>。原因已实测（2026-10-23，MySQL 8.4 + MyBatis-Plus 3.5.17）：
 * 租户插件按 SQL 里的<b>表名</b>注入 {@code tenant_id} 条件，而它认不出 {@code WITH RECURSIVE space_chain}
 * 是 CTE、不是表，于是生成
 * {@code INNER JOIN space_chain c ON s.id = c.pid AND c.tenant_id = 1} —— 运行时直接
 * {@code Unknown column 'c.tenant_id' in 'on clause'}，<b>空间树整个功能不可用</b>。
 * 更隐蔽的是：UPDATE 形态下插件**不会**给 CTE 注入，只给 {@code UPDATE} 的目标表加条件；
 * 两种语句行为不一致，靠肉眼审 SQL 审不出来，只能靠本注解 + 显式租户条件。</p>
 *
 * <p>跳过插件不等于放弃隔离：每条 SQL 的 {@code WHERE} 里都有 {@code tenant_id = #{tenantId}}，
 * 由调用方从 {@code TenantContext} 取当前租户传入（{@code null} 时调用方在取不到租户前就失败，
 * 不会走到 SQL）。这与「手写 tenant_id」在其它仓被禁止的情形不同——那些是<b>可以</b>交给插件的地方，
 * 这里是被插件的解析能力所迫。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
public interface SysSpaceMapper extends BaseMapper<SysSpace> {

    /**
     * 用<b>递归 CTE 一次取回</b>指定空间的完整父链（含自身），按「自身 → 根」倒序。
     *
     * <p><b>为什么必须是单条语句</b>：树深校验要沿 {@code pid} 逐层上溯。若在 Java 里
     * 「查一层、再查下一层」就是循环内 DB 调用（本仓 {@code SourceConventionTest} 的
     * {@code loopsMustNotCallDbOrRpc} 判红），且查询次数随树深增长。</p>
     *
     * @param spaceId  起始空间 ID（含自身）
     * @param tenantId 当前租户 ID（显式过滤，见类注释）
     * @param maxDepth CTE 递归深度上限（业务上限 + 余量）
     * @return 父链行（每行含 {@code id}/{@code pid}/{@code name}），按 depth 升序
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE space_chain AS (
            SELECT id, pid, name, 0 AS depth
            FROM sys_space
            WHERE id = #{spaceId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT s.id, s.pid, s.name, c.depth + 1
            FROM sys_space s
            INNER JOIN space_chain c ON s.id = c.pid
            WHERE s.tenant_id = #{tenantId} AND s.is_deleted = 0 AND c.depth < #{maxDepth}
        )
        SELECT id, pid, name FROM space_chain ORDER BY depth ASC
        """)
    List<Map<String, Object>> selectParentChain(@Param("spaceId") Long spaceId,
        @Param("tenantId") Long tenantId, @Param("maxDepth") int maxDepth);

    /**
     * 批量统计每个空间的直接子空间数与关联对象数（<b>一次分组查询</b>，不逐空间 count）。
     *
     * <p>本语句不含 CTE，租户插件可正常注入（两张表都在 {@code ypbin.tenant.ignore-tables} 之外），
     * 故此处<b>不</b>跳过租户插件。</p>
     *
     * @param spaceIds 空间主键（调用方保证非空）
     * @return 每行 {@code spaceId} / {@code childCount} / {@code relationCount}
     */
    @Select("""
        <script>
        SELECT s.id AS spaceId,
               (SELECT COUNT(*) FROM sys_space c WHERE c.pid = s.id AND c.is_deleted = 0) AS childCount,
               (SELECT COUNT(*) FROM sys_space_relation r WHERE r.space_id = s.id AND r.is_deleted = 0)
                   AS relationCount
        FROM sys_space s
        WHERE s.is_deleted = 0 AND s.id IN
        <foreach collection='spaceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
        </script>
        """)
    List<Map<String, Object>> countChildrenAndRelations(@Param("spaceIds") List<Long> spaceIds);

    /**
     * 空间换父后刷新<b>整棵子树</b>的 {@code level}（一次 SQL，不逐节点更新）。
     *
     * <p><b>UPDATE 形态下租户插件的行为与 SELECT 不同</b>（实测）：插件只给 {@code UPDATE} 的目标表
     * 追加 {@code target.tenant_id = ?}，<b>不会</b>进入 CTE 内部——所以这里不会像
     * {@link #selectParentChain} 那样报 Unknown column。但为了让两条 CTE 语句的租户口径
     * <b>完全一致</b>（都由本 SQL 显式控制，不依赖插件在不同语句类型下的差异行为），
     * 这里同样跳过插件并显式写租户条件。差异行为是「今天恰好能跑」，不是可依赖的契约。</p>
     *
     * @param rootId   变动子树的根空间 ID
     * @param tenantId 当前租户 ID（显式过滤）
     * @param newBase  该根空间的新层级
     * @param maxDepth CTE 递归深度上限（含余量）
     * @return 受影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
        WITH RECURSIVE subtree AS (
            SELECT id, 0 AS relDepth
            FROM sys_space
            WHERE id = #{rootId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT s.id, t.relDepth + 1
            FROM sys_space s
            INNER JOIN subtree t ON s.pid = t.id
            WHERE s.tenant_id = #{tenantId} AND s.is_deleted = 0 AND t.relDepth < #{maxDepth}
        )
        UPDATE sys_space target
        INNER JOIN subtree t ON target.id = t.id
        SET target.`level` = #{newBase} + t.relDepth
        WHERE target.tenant_id = #{tenantId}
        """)
    int refreshSubtreeLevel(@Param("rootId") Long rootId, @Param("tenantId") Long tenantId,
        @Param("newBase") int newBase, @Param("maxDepth") int maxDepth);

    /**
     * 取指定空间子树的最大相对深度（自身 = 0）。
     *
     * <p><b>为什么必须有</b>：{@code validateParent} 只校验「被移动节点自身」的新深度，
     * 拦不住「把它连同整棵子树挪到很深的位置」——实测把 3 层子树挪到第 4 层节点下，
     * 子树最深节点会变成第 7 层，超过 {@code MAX_SPACE_DEPTH = 6}；再挪一次可无限加深。
     * 一旦超过上限，后续在该子树下新建节点时 {@code validateParent} 会报「层级过深」，
     * 用户卡死无法操作（既有数据也再动不了）。</p>
     *
     * <p>返回的是<b>相对深度</b>（自身记为 0），调用方用
     * {@code 目标层级 + maxRelDepth <= MAX_SPACE_DEPTH} 判定，与 {@code level} 语义解耦——
     * 后者可能因历史数据脏而不准。</p>
     *
     * <p>本语句与 {@link #refreshSubtreeLevel} 共用同一 CTE 形状，租户口径一致。</p>
     *
     * @param rootId   子树根空间 ID
     * @param tenantId 当前租户 ID（显式过滤，见类注释）
     * @param maxDepth CTE 递归深度上限（含余量）
     * @return 最大相对深度；子树根不存在时返回 {@code null}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE subtree AS (
            SELECT id, 0 AS relDepth
            FROM sys_space
            WHERE id = #{rootId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT s.id, t.relDepth + 1
            FROM sys_space s
            INNER JOIN subtree t ON s.pid = t.id
            WHERE s.tenant_id = #{tenantId} AND s.is_deleted = 0 AND t.relDepth < #{maxDepth}
        )
        SELECT MAX(relDepth) FROM subtree
        """)
    Integer selectSubtreeMaxRelDepth(@Param("rootId") Long rootId, @Param("tenantId") Long tenantId,
        @Param("maxDepth") int maxDepth);

    /**
     * 懒加载树：查某一层的子节点（{@code pid} 相同），并**一次分组**算出各自有无子节点。
     *
     * <p><b>为什么要一次 SQL 带上 hasChildren</b>：若先查子节点、再对每个子节点查「有没有下级」，
     * 就是 N+1（一层 50 个节点 = 51 次查询）。这里用 {@code EXISTS} 子查询在同一个语句里算完。</p>
     *
     * <p>租户条件由插件自动追加（本语句无 CTE，插件能正确识别表名）。</p>
     *
     * @param parentId 父空间 ID（{@code 0} 表示查根节点）
     * @param keyword  名称/编码模糊匹配（可空；非空时**跨层级搜索**，不受 parentId 限制）
     * @return 该层节点（含 {@code hasChildren}）
     */
    @Select("""
        <script>
        SELECT s.id, s.pid, s.name, s.code,
               s.space_type AS spaceType, s.sort, s.status,
               (SELECT COUNT(*) FROM sys_space c WHERE c.pid = s.id AND c.is_deleted = 0) > 0
                   AS hasChildren
        FROM sys_space s
        WHERE s.is_deleted = 0
        <choose>
            <when test="keyword != null and keyword != ''">
                AND (s.name LIKE CONCAT('%', #{keyword}, '%')
                     OR s.code LIKE CONCAT('%', #{keyword}, '%'))
            </when>
            <otherwise>
                AND s.pid = #{parentId}
            </otherwise>
        </choose>
        ORDER BY s.sort ASC, s.id ASC
        </script>
        """)
    List<SpaceTreeNodeResp> selectChildrenWithHasChildren(@Param("parentId") Long parentId,
        @Param("keyword") String keyword);

    /**
     * 取指定空间**及其全部后代**的 ID（用于「按空间筛选」的含子树语义）。
     *
     * <p><b>为什么必须有</b>：设备挂在「A栋 1F 101室」，用户在设备列表选「A栋」筛选时，
     * 期望看到 A栋 下所有设备（含 1F、101室里的）。只按 {@code space_id = A栋} 匹配会漏掉
     * 全部实际挂在房间里的设备——而业务上设备几乎总是挂在最末级房间。</p>
     *
     * <p><b>为什么用一条 CTE 而不是 Java 递归</b>：Java 里逐层查子节点是循环内 DB 调用
     * （架构门禁判红），且查询次数随深度增长。CTE 一次算完。</p>
     *
     * <p>与其他 CTE 语句同一处置：跳过租户插件 + SQL 显式写 {@code tenant_id}
     * （插件认不出 CTE 名，会生成引用不存在列的 SQL）。</p>
     *
     * @param rootId   根空间 ID
     * @param tenantId 当前租户 ID
     * @param maxDepth CTE 递归深度上限（含余量）
     * @return 该空间及全部后代的 ID（含自身；根不存在时为空列表）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE subtree AS (
            SELECT id, 0 AS relDepth
            FROM sys_space
            WHERE id = #{rootId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT s.id, t.relDepth + 1
            FROM sys_space s
            INNER JOIN subtree t ON s.pid = t.id
            WHERE s.tenant_id = #{tenantId} AND s.is_deleted = 0 AND t.relDepth < #{maxDepth}
        )
        SELECT id FROM subtree ORDER BY relDepth ASC, id ASC
        """)
    List<Long> selectSubtreeIds(@Param("rootId") Long rootId, @Param("tenantId") Long tenantId,
        @Param("maxDepth") int maxDepth);
}
