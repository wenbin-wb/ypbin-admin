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
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.model.query.SpaceQuery;
import cn.ypbin.admin.system.model.req.SpaceSaveReq;
import cn.ypbin.admin.system.model.resp.SpaceResp;
import cn.ypbin.admin.system.model.resp.SpaceTreeNodeResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 空间服务。
 *
 * @author wenbin
 * @since 2026-10-23
 */
public interface SysSpaceService extends BaseService<SysSpace> {

    /**
     * 空间树（全量，按 pid + sort 排序后组树）。
     *
     * @return 空间树（叶子节点 children 为 null）
     */
    List<SpaceResp> tree();

    /**
     * 懒加载树：只查某一层（或按关键词跨层搜索）。
     *
     * <p><b>与 {@link #tree()} 的分工</b>：{@code tree()} 一次性返回全量树，适合空间数量小的租户
     * （几十~几百个，且要一次渲染完整层级）；本方法按需逐层加载，适合**万级空间**
     * ——全量树在那种量级下响应体会到 MB 级，且前端递归渲染会卡。</p>
     *
     * @param parentId 父空间 ID（{@code null}/{@code 0} 表示查根层）
     * @param keyword  关键词（非空时跨层级搜索，忽略 parentId）
     * @return 该层节点（含 {@code hasChildren}，供前端决定是否显示展开箭头）
     */
    List<SpaceTreeNodeResp> lazyTree(Long parentId, String keyword);

    /**
     * 空间平铺分页列表（供搜索/筛选）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SpaceResp> pageSpaces(SpaceQuery query);

    /**
     * 空间详情。
     *
     * @param id 空间 ID
     * @return 空间详情
     */
    SpaceResp getSpace(Long id);

    /**
     * 新增空间。
     *
     * @param req 请求
     * @return 新空间 ID
     */
    Long createSpace(SpaceSaveReq req);

    /**
     * 编辑空间。
     *
     * @param id  空间 ID
     * @param req 请求
     */
    void updateSpace(Long id, SpaceSaveReq req);

    /**
     * 删除空间（存在子空间或关联对象时拒绝）。
     *
     * @param id 空间 ID
     */
    void deleteSpace(Long id);

    /**
     * 取空间完整路径（如「总部园区 / A 栋 / 1F / 101 室」）。
     *
     * @param id 空间 ID
     * @return 路径；空间不存在时返回空串
     */
    String buildSpacePath(Long id);
}
