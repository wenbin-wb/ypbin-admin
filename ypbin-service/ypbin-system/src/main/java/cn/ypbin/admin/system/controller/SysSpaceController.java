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
package cn.ypbin.admin.system.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.model.query.SpaceQuery;
import cn.ypbin.admin.system.model.query.SpaceRelationQuery;
import cn.ypbin.admin.system.model.req.SpaceBindReq;
import cn.ypbin.admin.system.model.req.SpaceSaveReq;
import cn.ypbin.admin.system.model.req.SpaceSyncReq;
import cn.ypbin.admin.system.model.resp.SpaceRelationResp;
import cn.ypbin.admin.system.model.resp.SpaceResp;
import cn.ypbin.admin.system.model.resp.SpaceImportResultResp;
import cn.ypbin.admin.system.model.resp.SpaceTreeNodeResp;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.admin.system.service.SysSpaceService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tools.csv.CsvUtils;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 空间管理接口。
 *
 * <p>两段路径，语义分开：
 * <ul>
 *   <li>{@code /space/**} —— 空间主数据（树/列表/增删改）</li>
 *   <li>{@code /space/{id}/relations} 与 {@code /space/relations/**} —— 空间与业务对象的关联</li>
 * </ul>
 * 关联做成独立路径而不是塞进空间的 save/update：绑定/解绑是<b>高频、独立授权</b>的动作
 * （业务侧调、运维也调），混进主数据保存会让「改个备注」也要求绑定权限。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
public class SysSpaceController {

    private final SysSpaceService spaceService;

    private final SysSpaceRelationService relationService;

    /**
     * 空间树（全量）。
     *
     * @return 空间树
     */
    @GetMapping("/tree")
    @SaCheckPermission("system:space:list")
    public R<List<SpaceResp>> tree() {
        return R.ok(spaceService.tree());
    }

    /**
     * 懒加载树：只查一层（或按关键词跨层搜索）。
     *
     * <p>与 {@code /space/tree}（全量树）的分工：全量树适合空间数量小的租户，
     * 一次渲染完整层级；万级空间用本端点按需展开，避免 MB 级响应与客户端递归渲染卡顿。</p>
     *
     * @param parentId 父空间 ID（不传=查根层）
     * @param keyword  关键词（非空时跨层级搜索，忽略 parentId）
     * @return 该层节点（含 hasChildren）
     */
    @GetMapping("/tree/lazy")
    @SaCheckPermission("system:space:list")
    public R<List<SpaceTreeNodeResp>> lazyTree(
        @RequestParam(required = false) Long parentId,
        @RequestParam(required = false) String keyword) {
        return R.ok(spaceService.lazyTree(parentId, keyword));
    }

    /**
     * 空间平铺分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    @SaCheckPermission("system:space:list")
    public R<PageResult<SpaceResp>> list(SpaceQuery query) {
        return R.ok(spaceService.pageSpaces(query));
    }

    /**
     * 空间详情。
     *
     * @param id 空间 ID
     * @return 空间详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("system:space:list")
    public R<SpaceResp> detail(@PathVariable Long id) {
        return R.ok(spaceService.getSpace(id));
    }

    /**
     * 空间完整路径（如「总部园区 / A 栋 / 1F / 101 室」）。
     *
     * @param id 空间 ID
     * @return 路径
     */
    @GetMapping("/{id}/path")
    @SaCheckPermission("system:space:list")
    public R<String> path(@PathVariable Long id) {
        return R.ok(spaceService.buildSpacePath(id));
    }

    /**
     * 新增空间。
     *
     * @param req 请求
     * @return 新空间 ID
     */
    @Idempotent
    @Log(value = "新增空间", module = "空间管理")
    @PostMapping
    @SaCheckPermission("system:space:add")
    public R<Long> create(@Valid @RequestBody SpaceSaveReq req) {
        return R.ok(spaceService.createSpace(req));
    }

    /**
     * 编辑空间。
     *
     * @param id  空间 ID
     * @param req 请求
     * @return 空响应
     */
    @Idempotent
    @Log(value = "修改空间", module = "空间管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:space:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SpaceSaveReq req) {
        spaceService.updateSpace(id, req);
        return R.ok();
    }

    /**
     * 删除空间。
     *
     * @param id 空间 ID
     * @return 空响应
     */
    @Idempotent
    @Log(value = "删除空间", module = "空间管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:space:delete")
    public R<Void> delete(@PathVariable Long id) {
        spaceService.deleteSpace(id);
        return R.ok();
    }

    /**
     * 把一批业务对象绑定到指定空间。
     *
     * @param id  空间 ID
     * @param req 绑定请求
     * @return 实际绑定条数
     */
    @Idempotent
    @Log(value = "绑定空间关联", module = "空间管理")
    @PostMapping("/{id}/relations")
    @SaCheckPermission("system:space:bind")
    public R<Integer> bind(@PathVariable Long id, @Valid @RequestBody SpaceBindReq req) {
        return R.ok(relationService.bind(id, req));
    }

    /**
     * 把一批业务对象从指定空间解绑。
     *
     * @param id         空间 ID
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表
     * @return 实际删除条数
     */
    @Idempotent
    @Log(value = "解绑空间关联", module = "空间管理")
    @DeleteMapping("/{id}/relations")
    @SaCheckPermission("system:space:bind")
    public R<Integer> unbind(@PathVariable Long id,
        @RequestParam String targetType,
        @RequestParam List<Long> targetIds) {
        return R.ok(relationService.unbind(id, targetType, targetIds));
    }

    /**
     * 按业务对象**整体同步**其空间集合（一个事务内完成增删）。
     *
     * <p>表单保存的自然语义：「这个部门现在就在这几个空间」。调用方不需要自己算差集、
     * 发 N 次请求——那既慢又无事务（中途失败会留下半改状态）。</p>
     *
     * @param req 同步请求
     * @return 实际变更条数
     */
    @Idempotent
    @Log(value = "同步空间关联", module = "空间管理")
    @PostMapping("/relations/sync")
    @SaCheckPermission("system:space:bind")
    public R<Integer> syncRelations(@Valid @RequestBody SpaceSyncReq req) {
        return R.ok(relationService.syncTargetSpaces(req));
    }

    /**
     * 按空间反查其关联的业务对象（运维视角：这个空间挂了哪些对象）。
     *
     * @param id         空间 ID
     * @param targetType 关联对象类型（可选，null=不限）
     * @return 关联列表
     */
    @GetMapping("/{id}/relations")
    @SaCheckPermission("system:space:list")
    public R<List<SpaceRelationResp>> relationsBySpace(@PathVariable Long id,
        @RequestParam(required = false) String targetType) {
        return R.ok(relationService.listBySpace(id, targetType));
    }

    /**
     * 导出全部空间为 CSV（含父空间编码，可直接再导入）。
     *
     * <p>导出「父空间编码」而不是父 ID：导出的表格要能被人看懂、被人编辑后再导回来，
     * ID 是系统内部值，用户无法在离线表格里推算。</p>
     *
     * @return CSV 文件
     */
    @GetMapping("/export")
    @SaCheckPermission("system:space:list")
    public void export(HttpServletResponse response) {
        // 交给基座：CsvUtils.export 内聚了 RFC 4180 转义、公式注入中和、BOM、
        // 以及 RFC 5987 文件名编码（见 DownloadResponses）。
        // 自己拼一遍就等于把这几件事各写一份——基座注释明确警告过这种重复。
        CsvUtils.export(response, "space-" + LocalDate.now(),
            spaceService.exportHeaders(), spaceService.exportRows());
    }

    /**
     * 下载导入模板（表头 + 一行示例）。
     *
     * @return CSV 模板
     */
    @GetMapping("/import-template")
    @SaCheckPermission("system:space:list")
    public void importTemplate(HttpServletResponse response) {
        CsvUtils.export(response, "space-import-template",
            spaceService.exportHeaders(), spaceService.importTemplateRows());
    }

    /**
     * 从 CSV 导入空间。
     *
     * <p><b>逐行报错、不整体回滚</b>：一行出错不影响其它行——用户导入的是几百行，
     * 因一行格式错就全废，等于让他改一处再试一次、反复循环。</p>
     *
     * <p><b>mode 默认 insert</b>（已存在编码报错）；`upsert` 会按编码覆盖已有空间，
     * 属危险操作，必须调用方显式指定。</p>
     *
     * @param file CSV 文件
     * @param mode insert / upsert
     * @return 导入结果（含逐行错误明细）
     */
    @PostMapping("/import")
    @SaCheckPermission("system:space:add")
    @Idempotent
    @Log(value = "导入空间", module = "空间管理")
    public R<SpaceImportResultResp> importCsv(@RequestParam("file") MultipartFile file,
        @RequestParam(value = "mode", required = false) String mode) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件为空");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 读上传流失败是真故障（不是用户输入问题），必须抛出而不是当成「格式错」
            throw new BusinessException("读取导入文件失败：" + e.getMessage());
        }
        return R.ok(spaceService.importCsv(content, mode));
    }

    /**
     * 按空间（含全部后代）统计各类型业务对象数量（空间列表「设备数」等列用）。
     *
     * <p><b>为什么含后代</b>：设备挂在末级房间，用户看「A栋」那行时期望的数字包含其下所有设备。</p>
     *
     * <p><b>为什么一次传一批 spaceIds</b>：列表页一页几十行，逐行调就是 N 次请求。
     * 服务端对每个空间各跑一次子树 CTE（空间数是页面行数量级，不是业务数据量级），
     * 但有上限保护（见 `countBySpaceTrees`）。</p>
     *
     * @param spaceIds 根空间 ID 列表
     * @return 空间 ID → (对象类型 → 数量)
     */
    @PostMapping("/relation-counts")
    @SaCheckPermission("system:space:list")
    public R<Map<Long, Map<String, Long>>> relationCounts(@RequestBody List<Long> spaceIds) {
        return R.ok(relationService.countBySpaceTrees(spaceIds));
    }

    /**
     * 批量反查：一次查多个业务对象各自的空间（列表页「所在空间」列用）。
     *
     * <p>列表页有 N 条记录时，逐条调单对象反查就是 N 次 HTTP（N+1）；本端点一次返回。</p>
     *
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表
     * @return 对象 ID → 空间关联列表（无关联的对象不出现在结果里）
     */
    @GetMapping("/relations/by-targets")
    @SaCheckPermission("system:space:list")
    public R<Map<Long, List<SpaceRelationResp>>> relationsByTargets(
        @RequestParam String targetType,
        @RequestParam List<Long> targetIds) {
        return R.ok(relationService.listByTargets(targetType, targetIds));
    }

    /**
     * 按业务对象反查其所在空间。
     *
     * @param query 反查条件
     * @return 关联列表
     */
    @GetMapping("/relations/by-target")
    @SaCheckPermission("system:space:list")
    public R<List<SpaceRelationResp>> relationsByTarget(SpaceRelationQuery query) {
        return R.ok(relationService.listByTarget(query));
    }
}
