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
