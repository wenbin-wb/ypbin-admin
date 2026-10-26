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
import cn.ypbin.admin.system.model.resp.SpaceImportResultResp;
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

    /**
     * 取指定空间及其全部后代的 ID（供业务侧「按空间筛选」用）。
     *
     * <p><b>为什么业务侧需要它而不是自己按 pid 递归</b>：设备挂在最末级房间，
     * 用户选「A栋」筛选时期望含其下所有设备。业务服务（如 IoT）不持有空间表，
     * 也不该自己递归查 system 的表——故由 system 一次算好返回。</p>
     *
     * @param spaceId 根空间 ID
     * @return 该空间及全部后代 ID（含自身；不存在时为空列表）
     */
    List<Long> listSubtreeIds(Long spaceId);

    /**
     * 取导出用的表头。
     *
     * <p><b>为什么返回结构化数据而不是拼好的 CSV 文本</b>：CSV 的转义、BOM、响应头
     * 全部由基座的 {@code CsvUtils.export(response, fileName, headers, rows)} 负责
     * ——它内聚了 RFC 4180 转义、公式注入中和、RFC 5987 文件名编码。
     * 自己拼一遍就等于把这三件事各写一份（基座注释明确警告过这个坑）。</p>
     *
     * @return 表头
     */
    List<String> exportHeaders();

    /**
     * 取导出用的数据行（与 {@link #exportHeaders()} 的列一一对应）。
     *
     * <p>导出里写「父空间编码」而不是父 ID：导出的表格要能被人看懂、
     * 被人编辑后再导回来。ID 是系统内部值，用户无法在离线表格里推算。</p>
     *
     * @return 数据行
     */
    List<List<String>> exportRows();

    /**
     * 从 CSV 导入空间。
     *
     * <p><b>两遍扫描</b>：先解析全部行并做格式校验、建立「编码 → 待插入行」索引；
     * 再按父子依赖顺序逐层插入（子节点的父节点必须先存在）。
     * 单遍扫描无法处理「子行排在父行之前」的合法表格。</p>
     *
     * <p><b>逐行报错、不整体回滚</b>：一行出错不影响其它行——用户导入的是几百行，
     * 因一行格式错就全废，等于让他改一处再试一次、反复循环。</p>
     *
     * @param content CSV 文本（可带 BOM）
     * @param mode    {@code insert}（默认，已存在编码报错）或 {@code upsert}（存在则更新）
     * @return 导入结果（含逐行错误明细）
     */
    SpaceImportResultResp importCsv(String content, String mode);

    /**
     * 取导入模板的数据行（表头复用 {@link #exportHeaders()}，加一行示例）。
     *
     * <p>模板让用户不必猜列名与格式——列名拼错会被当「未知列」忽略，
     * 用户会以为填了却没生效。</p>
     *
     * @return 示例行
     */
    List<List<String>> importTemplateRows();
}
