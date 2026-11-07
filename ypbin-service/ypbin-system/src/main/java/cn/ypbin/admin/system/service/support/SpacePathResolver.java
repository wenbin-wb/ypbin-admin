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
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.system.constant.SpaceConstants;
import cn.ypbin.admin.system.entity.SysSpace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 空间「完整路径」的内存拼装：给一批节点，算出每个节点到根的 {@code A栋 / 1F / 101室}。
 *
 * <h2>为什么抽成独立类</h2>
 *
 * <p>此前有两份**逐字近似**的实现：{@code SysSpaceServiceImpl#buildPathMap}（列表/树页）
 * 与 {@code SysSpaceRelationServiceImpl#fillSpacePaths}（关联列表）。两者算法相同、
 * 守卫相同，但分隔符一处用常量一处硬编码 {@code " / "}，
 * 且 Long 比较一处用 {@code longValue()} 一处用引用比较。这类「同一套逻辑两份实现」
 * 修一处漏一处就会让两个页面的路径表现分裂。</p>
 *
 * <h2>为什么一次性取全量而不是逐条上溯查库</h2>
 *
 * <p>树/关联列表每行都要显示路径。逐条上溯就是 N 次递归 CTE；本类要求调用方
 * **一次**给出全量节点（{@code id/pid/name}），路径在内存里沿 {@code pid} 走，
 * 复杂度 O(n × 深度)，与请求行数无关。</p>
 *
 * <h2>两条硬约束</h2>
 *
 * <ul>
 *   <li><b>深度有界</b>：上溯最多 {@link SpaceConstants#MAX_SPACE_DEPTH} 层
 *       （{@code guard}），历史脏数据成环也不会死循环；</li>
 *   <li><b>Long 必须按值比较</b>：{@code pid == ROOT_PARENT_ID} 是引用比较，
 *       仅因 {@code 0L} 落在 Long 缓存区间才「碰巧」成立——根值一旦改成缓存外的数就会失效。
 *       本类统一用 {@code longValue()}。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-27
 */
public final class SpacePathResolver {

    /** 路径分隔符（展示口径唯一来源） */
    public static final String PATH_SEPARATOR = " / ";

    private SpacePathResolver() {
    }

    /**
     * 计算每个节点的完整路径。
     *
     * @param nodes 全量节点快照（只用 {@code id}/{@code pid}/{@code name}；调用方可用
     *              {@code select} 裁剪列以减少传输）
     * @return 节点 ID → 路径（父在前、子在后）。根节点单独出现（不含空段），
     *         节点在图里断链（父不存在）时只拼到能拼到的部分
     */
    public static Map<Long, String> resolve(List<SysSpace> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysSpace> byId = new HashMap<>(nodes.size());
        for (SysSpace node : nodes) {
            if (node != null && node.getId() != null) {
                byId.put(node.getId(), node);
            }
        }
        Map<Long, String> paths = new HashMap<>(nodes.size());
        for (SysSpace node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            String path = pathOf(node.getId(), byId);
            if (!path.isEmpty()) {
                paths.put(node.getId(), path);
            }
        }
        return paths;
    }

    /**
     * 计算单个节点的完整路径。
     *
     * @param spaceId 节点 ID
     * @param nodes   全量节点快照
     * @return 路径（父在前）；节点不存在或断链到根之外时返回空串
     */
    public static String pathOf(Long spaceId, List<SysSpace> nodes) {
        if (spaceId == null || nodes == null || nodes.isEmpty()) {
            return "";
        }
        Map<Long, SysSpace> byId = new HashMap<>(nodes.size());
        for (SysSpace node : nodes) {
            if (node != null && node.getId() != null) {
                byId.put(node.getId(), node);
            }
        }
        return pathOf(spaceId, byId);
    }

    /**
     * 计算单个节点的完整路径（内部分工：调用方已建好索引时复用它）。
     *
     * @param spaceId 节点 ID
     * @param byId    节点 ID → 节点 的索引
     * @return 路径（父在前）；节点不存在时返回空串
     */
    private static String pathOf(Long spaceId, Map<Long, SysSpace> byId) {
        List<String> names = new ArrayList<>();
        Long cursor = spaceId;
        // 上溯有界：最多走 MAX_SPACE_DEPTH 层，防历史脏数据成环导致死循环
        int guard = 0;
        while (cursor != null
            && cursor.longValue() != SpaceConstants.ROOT_PARENT_ID.longValue()
            && guard++ <= SpaceConstants.MAX_SPACE_DEPTH) {
            SysSpace node = byId.get(cursor);
            if (node == null) {
                break;
            }
            names.add(node.getName());
            cursor = node.getPid();
        }
        if (names.isEmpty()) {
            return "";
        }
        // 上溯是子→父，展示要父→子
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }
}
