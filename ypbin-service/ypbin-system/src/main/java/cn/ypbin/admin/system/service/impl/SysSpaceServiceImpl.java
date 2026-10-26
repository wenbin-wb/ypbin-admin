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
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.constant.SpaceConstants;
import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.entity.SysSpaceRelation;
import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.admin.system.mapper.SysSpaceMapper;
import cn.ypbin.admin.system.mapper.SysSpaceRelationMapper;
import cn.ypbin.admin.system.model.query.SpaceQuery;
import cn.ypbin.admin.system.model.req.SpaceSaveReq;
import cn.ypbin.starter.tools.csv.CsvDocument;
import cn.ypbin.starter.tools.csv.CsvLimits;
import cn.ypbin.starter.tools.csv.CsvRow;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.tools.csv.CsvUtils;
import cn.ypbin.admin.system.model.resp.SpaceResp;
import cn.ypbin.admin.system.model.resp.SpaceImportResultResp;
import cn.ypbin.admin.system.model.resp.SpaceTreeNodeResp;
import cn.ypbin.admin.system.service.SysSpaceService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.tree.TreeUtils;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 空间服务实现。
 *
 * <p><b>父链是唯一的不变量来源</b>：数据库约束能保证「父 ID 指向的行存在」（靠服务层），
 * 但拦不住「把空间挂到自己的后代下」（成环）、也拦不住无界深度。这两条只靠本类的
 * {@link #validateParent} 守——少写一行不会报错、不会红任何用例，只会让树页面无限递归。
 * 故校验逻辑与 {@code IotTwinSceneServiceImpl} 同构（同一套递归 CTE + 链尾必须到根 + 深度上限）。</p>
 *
 * <p><b>为什么不照抄 {@code SysDeptServiceImpl} 的校验</b>：部门侧的 {@code updateDept} 只判
 * 「父不能是自己」、不查父链成环、不查深度；而 starter 的 {@code TreeUtils.build} 遇环会
 * <b>把成环节点整棵从结果里丢掉</b>（它们既不是根、也挂不到任何根上），不抛错也不告警。
 * 空间树会挂设备/部门等业务数据，静默丢节点的后果比部门树更严重，因此这里必须自带三重校验。</p>
 *
 * <p><b>删除不级联</b>：级联删掉用户几个月的配置是不可逆事故。存在子空间或关联对象时一律拒绝，
 * 由用户逐层显式确认。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class SysSpaceServiceImpl extends BaseServiceImpl<SysSpaceMapper, SysSpace>
    implements SysSpaceService {

    private static final Logger log = LoggerFactory.getLogger(SysSpaceServiceImpl.class);

    /** 空间类型字典编码（与 002-data.sql 的 sys_dict.code 一致）。 */
    private static final String DICT_SPACE_TYPE = "space_type";

    /** 路径分隔符。 */
    private static final String PATH_SEPARATOR = " / ";

    private final SysSpaceRelationMapper relationMapper;

    private final SysDictItemMapper dictItemMapper;

    private final ObjectMapper objectMapper;

    @Override
    public List<SpaceResp> tree() {
        List<SysSpace> spaces = list(new LambdaQueryWrapper<SysSpace>()
            .orderByAsc(SysSpace::getPid)
            .orderByAsc(SysSpace::getSort)
            .orderByAsc(SysSpace::getId));
        // 把同一次查询结果同时用于「拼路径」：避免对同一张大表查两遍
        List<SpaceResp> tree = toRespList(spaces, spaces);
        List<SpaceResp> roots = TreeUtils.build(tree, SpaceResp::getId, SpaceResp::getPid,
            SpaceResp::setChildren, SpaceConstants.ROOT_PARENT_ID);
        // 与部门树同口径：TreeUtils 一律写回（可能为空的）列表 ⇒ 叶子会多出 "children":[]；
        // 而 SpaceResp 带 @JsonInclude(NON_NULL)——它只过滤 null、不过滤空集合。
        // 故显式把叶子的空 children 归一为 null，保持对外响应形态稳定。
        tree.forEach(node -> {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            }
        });
        return roots;
    }

    @Override
    public List<SpaceTreeNodeResp> lazyTree(Long parentId, String keyword) {
        long pid = normParentId(parentId);
        // 关键词搜索时忽略 parentId（跨层级找），由 SQL 的 choose 分支处理；
        // 空关键词才按层查。这里只做归一，不做业务判断。
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<SpaceTreeNodeResp> nodes = baseMapper.selectChildrenWithHasChildren(pid, kw);
        return nodes == null ? List.of() : nodes;
    }

    @Override
    public PageResult<SpaceResp> pageSpaces(SpaceQuery query) {
        PageResult<SysSpace> source = page(query, new LambdaQueryWrapper<SysSpace>()
            .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                .like(SysSpace::getName, query.getKeyword())
                .or()
                .like(SysSpace::getCode, query.getKeyword()))
            .eq(StringUtils.hasText(query.getSpaceType()), SysSpace::getSpaceType, query.getSpaceType())
            .eq(query.getPid() != null, SysSpace::getPid, query.getPid())
            .eq(query.getStatus() != null, SysSpace::getStatus, query.getStatus())
            .orderByAsc(SysSpace::getPid)
            .orderByAsc(SysSpace::getSort)
            .orderByAsc(SysSpace::getId));
        List<SpaceResp> items = toRespList(source.getItems());
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public SpaceResp getSpace(Long id) {
        SysSpace space = requireSpace(id);
        return toRespList(List.of(space)).getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpace(SpaceSaveReq req) {
        String extJson = normalizeExtJson(req.getExtJson());
        long pid = normParentId(req.getPid());
        int level = validateParent(null, pid);
        checkCodeUnique(req.getCode(), null);
        requireSpaceType(req.getSpaceType());

        SysSpace space = new SysSpace();
        apply(space, req, pid, level, extJson);
        if (!save(space)) {
            throw new BusinessException("空间创建失败");
        }
        return space.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpace(Long id, SpaceSaveReq req) {
        SysSpace space = requireSpace(id);
        String extJson = normalizeExtJson(req.getExtJson());
        long pid = normParentId(req.getPid());
        int newLevel = validateParent(id, pid);
        checkCodeUnique(req.getCode(), id);
        requireSpaceType(req.getSpaceType());

        int oldLevel = space.getLevel() == null ? 1 : space.getLevel();
        long oldPid = space.getPid() == null ? SpaceConstants.ROOT_PARENT_ID : space.getPid();
        apply(space, req, pid, newLevel, extJson);
        if (!updateById(space)) {
            throw new BusinessException("空间更新失败");
        }
        // 整棵子树的 level 必须跟着改：子树的「还能不能再加深」判定用的是库里存的 level，
        // 只改自己不改子树，原本到顶的节点会按旧值被放过。
        // 触发条件是「父变了 或 层级变了」——**只看层级会漏掉同层换父**：
        // 从 A(第2层) 挪到 B(第2层) 时层级不变，但子树每层的绝对深度基准没变、无需刷新；
        // 而从 A(第2层，其子为第3层) 挪到 B(第1层) 时 newLevel != oldLevel 已覆盖。
        // 两者都要判，是因为旧数据 level 可能已脏（历史手工改库里数据），此时层级相等但树已错位。
        if (pid != oldPid || newLevel != oldLevel) {
            refreshSubtreeLevel(space.getId(), newLevel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpace(Long id) {
        SysSpace space = requireSpace(id);
        Long childCount = baseMapper.selectCount(new LambdaQueryWrapper<SysSpace>()
            .eq(SysSpace::getPid, space.getId()));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该空间下还有 " + childCount + " 个子空间，无法删除："
                + "请先删除（或移走）子空间。平台刻意不做级联删除，避免误删掉整棵空间树");
        }
        Long relationCount = relationMapper.selectCount(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getSpaceId, id));
        if (relationCount != null && relationCount > 0) {
            throw new BusinessException("该空间上仍挂着 " + relationCount + " 个业务对象（部门/人员/设备等），"
                + "无法删除：请先解绑。删空间不会替你决定这些对象去哪");
        }
        if (!removeById(id)) {
            throw new BusinessException("空间删除失败");
        }
    }

    @Override
    public String buildSpacePath(Long id) {
        if (id == null) {
            return "";
        }
        List<Map<String, Object>> chain = baseMapper.selectParentChain(id, currentTenantId(),
            SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN);
        if (chain.isEmpty()) {
            return "";
        }
        // CTE 按 depth 升序返回（自身 → 根），拼路径需反过来（根 → 自身）
        List<String> names = new ArrayList<>(chain.size());
        for (Map<String, Object> row : chain) {
            Object name = row.get("name");
            names.add(name == null ? "" : name.toString());
        }
        Collections.reverse(names);
        return String.join(PATH_SEPARATOR, names);
    }

    @Override
    public List<Long> listSubtreeIds(Long spaceId) {
        if (spaceId == null) {
            return List.of();
        }
        List<Long> ids = baseMapper.selectSubtreeIds(spaceId, currentTenantId(),
            SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN);
        return ids == null ? List.of() : ids;
    }

    // ---------- CSV 导入 / 导出 ----------

    /**
     * 公式注入中和可能加的前缀字符（与基座 {@code CsvUtils.FORMULA_PREFIXES} 一致）。
     *
     * <p>导入时要把这些前缀剥掉，否则导出后的负数无法回导。</p>
     */
    private static final String NEUTRALIZED_PREFIXES = "=+-@\t\r";

    /** 导出列顺序（表头即列名，导入按同一套名字认列）。 */
    private static final List<String> SPACE_CSV_HEADERS = List.of(
        "code", "parentCode", "name", "spaceType", "sort", "status",
        "address", "longitude", "latitude", "floorNo", "roomNo",
        "area", "capacity", "manager", "phone", "remark");

    @Override
    public List<String> exportHeaders() {
        return SPACE_CSV_HEADERS;
    }

    @Override
    public List<List<String>> exportRows() {
        List<SysSpace> all = list(new LambdaQueryWrapper<SysSpace>()
            .orderByAsc(SysSpace::getPid)
            .orderByAsc(SysSpace::getSort)
            .orderByAsc(SysSpace::getId));
        Map<Long, String> codeById = new HashMap<>(all.size());
        for (SysSpace space : all) {
            codeById.put(space.getId(), space.getCode());
        }
        List<List<String>> rows = new ArrayList<>(all.size());
        for (SysSpace space : all) {
            long pid = space.getPid() == null ? SpaceConstants.ROOT_PARENT_ID : space.getPid();
            // 根节点的 parentCode 留空（而不是写 "0"）：用户看表格时「空 = 顶层」最直观
            String parentCode = pid == SpaceConstants.ROOT_PARENT_ID ? ""
                : codeById.getOrDefault(pid, "");
            rows.add(Arrays.asList(
                space.getCode(),
                parentCode,
                space.getName(),
                space.getSpaceType(),
                space.getSort() == null ? null : String.valueOf(space.getSort()),
                space.getStatus() == null ? null : String.valueOf(space.getStatus()),
                space.getAddress(),
                space.getLongitude() == null ? null : space.getLongitude().toPlainString(),
                space.getLatitude() == null ? null : space.getLatitude().toPlainString(),
                space.getFloorNo(),
                space.getRoomNo(),
                space.getArea() == null ? null : space.getArea().toPlainString(),
                space.getCapacity() == null ? null : String.valueOf(space.getCapacity()),
                space.getManager(),
                space.getPhone(),
                space.getRemark()));
        }
        return rows;
    }

    @Override
    public List<List<String>> importTemplateRows() {
        return List.of(Arrays.asList(
            "HQ", "", "总部园区", "park", "1", "1",
            null, null, null, null, null, null, null, null, null, "示例行，可删除"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceImportResultResp importCsv(String content, String mode) {
        SpaceImportResultResp result = new SpaceImportResultResp();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("导入内容为空");
        }
        String effectiveMode = StringUtils.hasText(mode)
            ? mode.trim().toLowerCase(Locale.ROOT) : SpaceConstants.IMPORT_MODE_INSERT;
        if (!SpaceConstants.IMPORT_MODE_INSERT.equals(effectiveMode)
            && !SpaceConstants.IMPORT_MODE_UPSERT.equals(effectiveMode)) {
            throw new BusinessException("不支持的导入模式：" + mode
                + "（允许 " + SpaceConstants.IMPORT_MODE_INSERT + " / "
                + SpaceConstants.IMPORT_MODE_UPSERT + "）");
        }

        CsvDocument doc = CsvUtils.parseText(content,
            new CsvLimits(CsvLimits.DEFAULT_MAX_BYTES, SpaceConstants.MAX_IMPORT_ROWS + 1,
                CsvLimits.DEFAULT_MAX_COLUMNS));
        List<CsvRow> allRows = doc.rows();
        if (allRows.isEmpty()) {
            throw new BusinessException("CSV 内容为空（至少需要一行表头）");
        }
        // 表头是第一条记录（CsvDocument 不单独暴露 header）
        List<String> header = allRows.getFirst().cells();
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            // 去掉 BOM 与首尾空白：Excel 另存的 CSV 表头常带 BOM 与空格
            String raw = header.get(i);
            String name = raw == null ? "" : raw.replace("\uFEFF", "").trim();
            if (!name.isEmpty()) {
                colIndex.put(name, i);
            }
        }
        // 必需列缺失属「整张表不可用」，直接说明缺哪列——继续逐行报错只会刷屏
        for (String required : List.of("code", "name", "spaceType")) {
            if (!colIndex.containsKey(required)) {
                throw new BusinessException("CSV 缺少必需列：" + required);
            }
        }

        // ---- 第一遍：解析全部行 + 格式校验，建立索引 ----
        List<ImportRow> parsed = new ArrayList<>();
        Map<String, ImportRow> byCode = new LinkedHashMap<>();
        Map<String, Long> existingIdByCode = loadExistingIdByCode();
        // 记录「表格里出现过、但因自身错误被丢弃」的编码：
        // 它的子行会报「父不存在」，若不区分，用户会去补一个其实已存在（只是重复/非法）的父
        Map<String, Integer> rejectedCodeLine = new LinkedHashMap<>();
        // 跳过表头（第 0 条）；每条 CsvRow 自带物理行号，直接用于报错定位
        for (int i = 1; i < allRows.size(); i++) {
            CsvRow csvRow = allRows.get(i);
            int lineNo = csvRow.lineNumber();
            List<String> cells = csvRow.cells();
            String code = cell(cells, colIndex, "code");
            try {
                ImportRow row = parseImportRow(cells, colIndex, lineNo);
                if (byCode.containsKey(row.code)) {
                    throw new BusinessException(
                        "编码重复（表格内已出现于第 " + byCode.get(row.code).lineNo + " 行）");
                }
                // insert 模式下编码已存在 = 错误；upsert 模式下是更新目标
                if (SpaceConstants.IMPORT_MODE_INSERT.equals(effectiveMode)
                    && existingIdByCode.containsKey(row.code)) {
                    throw new BusinessException(
                        "编码已存在（如需覆盖请改用 upsert 模式）");
                }
                byCode.put(row.code, row);
                parsed.add(row);
            } catch (BusinessException e) {
                result.addError(lineNo, code, e.getMessage(), SpaceConstants.MAX_IMPORT_ERRORS);
                if (StringUtils.hasText(code)) {
                    rejectedCodeLine.putIfAbsent(code, lineNo);
                }
            }
        }

        // ---- 第二遍：按父子依赖逐层插入 ----
        // 每轮处理「父已就绪」的行；一轮没有任何进展说明存在环或父编码不存在，
        // 剩下的行全部报错并写明原因（否则会静默丢失）。
        Map<String, Long> idByCode = new HashMap<>(existingIdByCode);
        Set<ImportRow> pending = new LinkedHashSet<>(parsed);
        while (!pending.isEmpty()) {
            List<ImportRow> ready = new ArrayList<>();
            for (ImportRow row : pending) {
                if (!StringUtils.hasText(row.parentCode)) {
                    ready.add(row);         // 根节点
                } else if (idByCode.containsKey(row.parentCode)) {
                    ready.add(row);         // 父已就绪
                }
            }
            if (ready.isEmpty()) {
                // 剩余行的父编码全部找不到 ⇒ 环或父缺失。逐行给出可行动的原因。
                for (ImportRow row : pending) {
                    String reason;
                    if (byCode.containsKey(row.parentCode)) {
                        reason = "父子关系成环（parentCode=" + row.parentCode + " 间接指向自己）";
                    } else if (rejectedCodeLine.containsKey(row.parentCode)) {
                        // 父行本身已报错被丢弃：直接指明，避免用户以为是「父编码写错了」去补一个已存在的父
                        reason = "其父空间「" + row.parentCode + "」在第 "
                            + rejectedCodeLine.get(row.parentCode)
                            + " 行已校验失败，本行无法挂载（请先修正那一行）";
                    } else {
                        reason = "父空间编码不存在：parentCode=" + row.parentCode;
                    }
                    result.addError(row.lineNo, row.code, reason,
                        SpaceConstants.MAX_IMPORT_ERRORS);
                }
                break;
            }
            for (ImportRow row : ready) {
                try {
                    if (idByCode.containsKey(row.code)) {
                        applyUpsert(row, idByCode.get(row.code), idByCode);
                        result.setUpdated(result.getUpdated() + 1);
                    } else {
                        Long newId = createSpace(row.toSaveReq(idByCode.get(row.parentCode)));
                        idByCode.put(row.code, newId);
                        result.setInserted(result.getInserted() + 1);
                    }
                    pending.remove(row);
                } catch (BusinessException e) {
                    result.addError(row.lineNo, row.code, e.getMessage(),
                        SpaceConstants.MAX_IMPORT_ERRORS);
                    pending.remove(row);
                } catch (DuplicateKeyException e) {
                    // 数据库唯一键冲突（如并发导入同一份文件、编码与已有行撞 uk_space_tenant_code）。
                    // 必须**逐行归因**而不能让它冒泡：冒泡会触发整个 @Transactional 回滚，
                    // 用户看到的是「整批失败 + 一条 SQL 异常」，与「逐行报错」的设计意图相反。
                    result.addError(row.lineNo, row.code,
                        "编码已存在（数据库唯一约束冲突，可能有并发导入）",
                        SpaceConstants.MAX_IMPORT_ERRORS);
                    pending.remove(row);
                }
            }
        }
        return result;
    }

    /**
     * upsert 模式：按编码更新已存在的空间。
     *
     * <p><b>整行覆盖</b>（与 `updateSpace` 一致）：CSV 一行就是一份完整的空间定义。
     * 若只更新部分列，用户会误以为「表格里的空值表示保持不变」，而实际上空值会清空字段。</p>
     *
     * @param row       导入行
     * @param id        已存在的空间 ID
     * @param idByCode  编码 → 空间 ID 映射（含本次已插入的行）
     * @throws BusinessException 父编码无法解析
     */
    private void applyUpsert(ImportRow row, Long id, Map<String, Long> idByCode) {
        updateSpace(id, row.toSaveReq(resolveParentId(row, idByCode)));
    }

    /**
     * 由父编码解析父空间 ID。
     *
     * <p><b>为什么必须在映射里解析、解析不到就报错</b>：</p>
     * <ul>
     *   <li>映射（{@code idByCode}）已含全部既有空间与本次已插入的行，**不必再查库**
     *       ——否则 5000 行导入就是 5000 次多余查询；</li>
     *   <li>解析不到时<b>不能回退根节点</b>：那会把本该报错的子节点静默挂成顶级空间，
     *       与第一遍扫描的严格校验（报「父空间编码不存在」）自相矛盾。</li>
     * </ul>
     *
     * @param row      导入行
     * @param idByCode 编码 → 空间 ID 映射
     * @return 父空间 ID（根节点为 ROOT_PARENT_ID）
     * @throws BusinessException 父编码在映射中不存在
     */
    private Long resolveParentId(ImportRow row, Map<String, Long> idByCode) {
        if (!StringUtils.hasText(row.parentCode)) {
            return SpaceConstants.ROOT_PARENT_ID;
        }
        Long parentId = idByCode.get(row.parentCode);
        if (parentId == null) {
            throw new BusinessException("父空间编码不存在：parentCode=" + row.parentCode);
        }
        return parentId;
    }

    /**
     * 载入「已存在编码 → ID」（一次查询，不逐行查）。
     *
     * @return 编码 → 空间 ID
     */
    private Map<String, Long> loadExistingIdByCode() {
        List<SysSpace> all = list(new LambdaQueryWrapper<SysSpace>()
            .select(SysSpace::getId, SysSpace::getCode));
        Map<String, Long> map = new HashMap<>(all.size());
        for (SysSpace space : all) {
            if (space.getCode() != null) {
                map.put(space.getCode(), space.getId());
            }
        }
        return map;
    }

    /**
     * 解析并校验一行导入数据。
     *
     * @param cells    该行单元格
     * @param colIndex 列名 → 列下标
     * @param lineNo   物理行号（用于报错定位）
     * @return 导入行
     * @throws BusinessException 该行格式或取值不合法
     */
    private ImportRow parseImportRow(List<String> cells, Map<String, Integer> colIndex, int lineNo) {
        ImportRow row = new ImportRow();
        row.lineNo = lineNo;
        row.code = requireCell(cells, colIndex, "code", SpaceConstants.MAX_CODE_LENGTH);
        row.name = requireCell(cells, colIndex, "name", SpaceConstants.MAX_NAME_LENGTH);
        row.spaceType = requireCell(cells, colIndex, "spaceType",
            SpaceConstants.MAX_SPACE_TYPE_LENGTH);
        // 类型必须是字典已登记的项值（与 createSpace 同一判据，不另立一套）
        requireSpaceType(row.spaceType);
        row.parentCode = cell(cells, colIndex, "parentCode");
        if (StringUtils.hasText(row.parentCode) && row.parentCode.equals(row.code)) {
            throw new BusinessException("parentCode 不能等于自身 code");
        }
        row.sort = parseIntCell(cells, colIndex, "sort");
        row.status = parseIntCell(cells, colIndex, "status");
        row.address = limitCell(cells, colIndex, "address", SpaceConstants.MAX_ADDRESS_LENGTH);
        row.floorNo = limitCell(cells, colIndex, "floorNo", SpaceConstants.MAX_FLOOR_NO_LENGTH);
        row.roomNo = limitCell(cells, colIndex, "roomNo", SpaceConstants.MAX_ROOM_NO_LENGTH);
        row.manager = limitCell(cells, colIndex, "manager", SpaceConstants.MAX_MANAGER_LENGTH);
        row.phone = limitCell(cells, colIndex, "phone", SpaceConstants.MAX_PHONE_LENGTH);
        row.remark = limitCell(cells, colIndex, "remark", SpaceConstants.MAX_REMARK_LENGTH);
        row.longitude = parseDecimalCell(cells, colIndex, "longitude");
        row.latitude = parseDecimalCell(cells, colIndex, "latitude");
        row.area = parseDecimalCell(cells, colIndex, "area");
        row.capacity = parseIntCell(cells, colIndex, "capacity");
        return row;
    }

    /**
     * 取单元格文本（去首尾空白，并把公式注入中和的前缀剥掉）。
     *
     * <p><b>为什么要剥前导单引号</b>：导出侧 {@code CsvUtils.escapeCell} 会对首字符命中
     * {@code = + - @ \t \r} 的单元格加 {@code '} 前缀（公式注入中和）。而负数
     * （{@code sort=-1}、{@code latitude=-33.8}、{@code longitude=-180}）是**合法业务值**，
     * 导出后变成 {@code '-1}，若不剥回来，导出的文件就无法再导入
     * （回导报「sort 不是合法整数：'-1」）——「导出必须能直接再导回去」是自定契约，
     * 被这一处前缀打破。</p>
     *
     * <p>只在「第二个字符命中中和前缀集合」时才剥，避免误伤以引号开头的正常文本。</p>
     *
     * @param cells    该行单元格
     * @param colIndex 列名 → 列下标
     * @param name     列名
     * @return 去空白与中和前缀后的值（列不存在时为 null）
     */
    private String cell(List<String> cells, Map<String, Integer> colIndex, String name) {
        Integer idx = colIndex.get(name);
        if (idx == null || idx >= cells.size()) {
            return null;
        }
        String value = cells.get(idx);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // 剥掉 CsvUtils 为防公式注入加的前缀（如 '-1 → -1）
        if (trimmed.length() > 1 && trimmed.charAt(0) == '\''
            && NEUTRALIZED_PREFIXES.indexOf(trimmed.charAt(1)) >= 0) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private String requireCell(List<String> cells, Map<String, Integer> colIndex, String name,
        int maxLength) {
        String value = cell(cells, colIndex, name);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("缺少必填列值：" + name);
        }
        if (value.length() > maxLength) {
            throw new BusinessException(name + " 超过最大长度 " + maxLength);
        }
        return value;
    }

    private String limitCell(List<String> cells, Map<String, Integer> colIndex, String name,
        int maxLength) {
        String value = cell(cells, colIndex, name);
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(name + " 超过最大长度 " + maxLength);
        }
        return StringUtils.hasText(value) ? value : null;
    }

    private Integer parseIntCell(List<String> cells, Map<String, Integer> colIndex, String name) {
        String value = cell(cells, colIndex, name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(name + " 不是合法整数：" + value);
        }
    }

    private BigDecimal parseDecimalCell(List<String> cells, Map<String, Integer> colIndex,
        String name) {
        String value = cell(cells, colIndex, name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(name + " 不是合法数字：" + value);
        }
    }

    /**
     * 导入时的一行（解析后的中间形态）。
     *
     * <p>用可变字段而非 record：解析阶段逐列赋值，且 {@code lineNo} 在构造后才可用。</p>
     */
    private static final class ImportRow {
        private int lineNo;
        private String code;
        private String parentCode;
        private String name;
        private String spaceType;
        private Integer sort;
        private Integer status;
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private String floorNo;
        private String roomNo;
        private BigDecimal area;
        private Integer capacity;
        private String manager;
        private String phone;
        private String remark;

        /**
         * 转成创建/更新请求。
         *
         * @param parentId 父空间 ID（根节点传 ROOT_PARENT_ID）
         * @return 保存请求
         */
        private SpaceSaveReq toSaveReq(Long parentId) {
            SpaceSaveReq req = new SpaceSaveReq();
            req.setPid(parentId == null ? SpaceConstants.ROOT_PARENT_ID : parentId);
            req.setName(name);
            req.setCode(code);
            req.setSpaceType(spaceType);
            req.setSort(sort == null ? 0 : sort);
            // 用 getCode() 而非裸写 1：本仓禁止裸状态值（见 EntityStatus 类注释）
            req.setStatus(status == null ? EntityStatus.ENABLED.getCode() : status);
            req.setAddress(address);
            req.setLongitude(longitude);
            req.setLatitude(latitude);
            req.setFloorNo(floorNo);
            req.setRoomNo(roomNo);
            req.setArea(area);
            req.setCapacity(capacity);
            req.setManager(manager);
            req.setPhone(phone);
            req.setRemark(remark);
            return req;
        }
    }

    // ---------- 校验 ----------

    /**
     * 校验父空间与树深（同时防成环）。
     *
     * <p>上溯上限用 {@code MAX_SPACE_DEPTH + PARENT_CHAIN_MARGIN}：正常树远达不到，一旦遇到历史脏数据
     * 留下的环，到上限即拒绝并明确报「父子关系成环」，保证查询次数有界、不会无界循环。</p>
     *
     * <p><b>为什么返回层级而不是 void</b>：层级就等于「父链长度 + 1」，已经在本次查询里算出来了。
     * 若调用方再算一次就是同一条递归 CTE 跑两遍——既是白费的往返，
     * 也留下「两次查询之间父链被改」的竞态窗口（校验用的父链与算层级用的父链可能不同）。
     * 故让本方法把结果一并返回，调用方不再重复查询。</p>
     *
     * @param selfId   当前空间主键（新建时为 null）
     * @param parentId 归一后的父空间 ID（0=根）
     * @return 该空间应处的层级（根的子 = 2，顶级 = 1）
     */
    private int validateParent(Long selfId, long parentId) {
        if (parentId == SpaceConstants.ROOT_PARENT_ID) {
            return 1;
        }
        if (selfId != null && selfId.longValue() == parentId) {
            throw new BusinessException("空间不能作为自己的父空间");
        }
        // 一条递归 CTE 取回整条父链（不在 Java 里逐层查库：那是循环内 DB 调用）
        List<Map<String, Object>> chain = baseMapper.selectParentChain(parentId, currentTenantId(),
            SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN);
        if (chain.isEmpty()) {
            throw new BusinessException("父空间不存在：" + parentId);
        }
        // CTE 按 depth 升序返回：chain[0] 是候选父，链尾是根（pid=0）或断点
        int chainLength = chain.size();
        for (Map<String, Object> row : chain) {
            long nodeId = asLong(row.get("id"));
            if (selfId != null && selfId.longValue() == nodeId) {
                throw new BusinessException("空间不能挂到自己的后代下（父链上出现了当前空间本身）");
            }
        }
        // 链尾必须是根（pid=0）；否则说明父链断裂或成环
        Map<String, Object> tail = chain.getLast();
        long tailParent = asLong(tail.get("pid"));
        if (tailParent != SpaceConstants.ROOT_PARENT_ID.longValue()) {
            throw new BusinessException("空间层级数据异常（父子关系成环或层级过深）："
                + "请检查空间树的父空间配置");
        }
        // 新深度 = 父链长度 + 自身一层
        int newDepth = chainLength + 1;
        if (newDepth > SpaceConstants.MAX_SPACE_DEPTH) {
            throw new BusinessException("空间层级不能超过 " + SpaceConstants.MAX_SPACE_DEPTH
                + " 层（当前将达第 " + newDepth + " 层）");
        }
        // **整棵子树也要校验**：只校验自己被移动后的深度拦不住「拖着深子树往下挪」——
        // 实测把 3 层子树挪到第 4 层节点下，子树最深节点会到第 7 层（超上限 1 层），
        // 再挪一次可无限加深；之后在该子树下新建节点会被这里报「层级过深」而卡死。
        if (selfId != null) {
            Integer maxRelDepth = baseMapper.selectSubtreeMaxRelDepth(selfId, currentTenantId(),
                SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN);
            int deepest = newDepth + (maxRelDepth == null ? 0 : maxRelDepth);
            if (deepest > SpaceConstants.MAX_SPACE_DEPTH) {
                throw new BusinessException("移动后该空间的子树将达第 " + deepest + " 层，超过上限 "
                    + SpaceConstants.MAX_SPACE_DEPTH + " 层：请先精简子树再移动，"
                    + "或把它挂到更浅的位置");
            }
        }
        return newDepth;
    }

    /**
     * 父空间层级变化后，刷新整棵子树的 {@code level}。
     *
     * <p><b>为什么必须刷新</b>：{@code level} 参与「能否再加深」的判定与前端层级展示。
     * 若只改自己、不改子树，子树里那些原本到顶的空间在库里仍记着旧深度，
     * 后续再往下加时会按旧值判定「还没到顶」而放过。</p>
     *
     * <p><b>为什么用一条 SQL</b>：子树可能几十上百个节点，逐条 update 是循环内 DB 调用（门禁判红）。
     * 这里用递归 CTE 算出新层级后一次 {@code UPDATE ... JOIN}，把「N 次写」压成「1 次写」。</p>
     *
     * @param rootId  变动子树的根空间 ID
     * @param newBase 该根空间的新层级
     */
    private void refreshSubtreeLevel(Long rootId, int newBase) {
        // 子树内每个节点的新层级 = 自身相对根的相对深度 + 根的新层级
        baseMapper.refreshSubtreeLevel(rootId, currentTenantId(), newBase,
            SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN);
    }

    /**
     * 校验空间类型必须是字典 {@code space_type} 里登记过的项值。
     *
     * <p><b>为什么要校验</b>：类型值会参与列表过滤（{@code idx_space_tenant_type}）与字典翻译。
     * 若不校验，直接调 API 能写入任意字符串——这些行在页面上翻译不出中文
     * （{@code @DictText} 查不到字典项时原样输出编码），按类型筛选也永远匹配不上，
     * 而库里没有任何报错。
     * 与关联类型 {@code space_target_type} 的校验同口径：字典是取值的单一来源。</p>
     *
     * @param spaceType 空间类型
     */
    private void requireSpaceType(String spaceType) {
        if (!StringUtils.hasText(spaceType)) {
            throw new BusinessException("空间类型不能为空");
        }
        for (SysDictItem item : dictItemMapper.selectByDictCode(DICT_SPACE_TYPE)) {
            if (spaceType.trim().equals(item.getValue())) {
                return;
            }
        }
        throw new BusinessException("空间类型不合法：" + spaceType
            + "（允许值见字典 space_type，新增类型请先在字典里加项）");
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysSpace>()
            .eq(SysSpace::getCode, code)
            .ne(excludeId != null, SysSpace::getId, excludeId));
        if (exists != null && exists > 0) {
            throw new BusinessException("空间编码已存在：" + code);
        }
    }

    private SysSpace requireSpace(Long id) {
        SysSpace space = getById(id);
        if (space == null) {
            throw new BusinessException("空间不存在或无权操作");
        }
        return space;
    }

    /**
     * 取当前租户 ID（<b>与 {@code SysPostServiceImpl} 同口径</b>：走 {@link UserContext}）。
     *
     * <p><b>为什么必须显式取</b>：{@link SysSpaceMapper#selectParentChain} 与
     * {@link SysSpaceMapper#refreshSubtreeLevel} 是递归 CTE 语句，租户插件认不出 CTE 名、
     * 会生成引用不存在列的 SQL（实测直接报 Unknown column）。故这两条语句跳过插件、
     * 由本方法把租户条件写进 SQL。</p>
     *
     * <p><b>为什么用 {@code UserContext} 而不是 {@code TenantContext}</b>（2026-10-23 实测核对）：
     * starter 的租户取值链路是 {@code TenantContext.getTenantId().orElse(tenantProvider)}，
     * 其中 {@code TenantContext} 存的是<b>显式覆盖值</b>（只有 {@code executeWithTenant} 才写），
     * 正常请求下<b>为空</b>；真正的来源是 {@code IdentityTenantProvider → IdentityContext}
     * （网关签发的身份头）。{@link UserContext#getTenantId()} 正是读身份头，
     * 与 {@code SysPostServiceImpl} 的既有写法一致。若这里用 {@code TenantContext}，
     * 生产环境每次都会抛「无法确定当前租户」——功能整个不可用。</p>
     *
     * <p>取不到租户时<b>显式失败</b>而不是退化成「不加条件」：后者会让一个租户看到
     * 另一个租户的空间树，是越权而非降级。</p>
     *
     * @return 当前租户 ID
     */
    private Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户"));
    }

    private long normParentId(Long pid) {
        return pid == null ? SpaceConstants.ROOT_PARENT_ID : pid;
    }

    private void apply(SysSpace space, SpaceSaveReq req, long pid, int level, String extJson) {
        // 字段全程同名，直接逐个赋值（不用改名映射）；level 由服务端计算，不取信入参
        space.setPid(pid);
        space.setName(req.getName());
        space.setCode(StringUtils.hasText(req.getCode()) ? req.getCode().trim() : null);
        space.setSpaceType(req.getSpaceType());
        space.setLevel(level);
        space.setSort(req.getSort() == null ? 0 : req.getSort());
        space.setAddress(req.getAddress());
        space.setLongitude(req.getLongitude());
        space.setLatitude(req.getLatitude());
        space.setFloorNo(req.getFloorNo());
        space.setRoomNo(req.getRoomNo());
        space.setArea(req.getArea());
        space.setCapacity(req.getCapacity());
        space.setManager(req.getManager());
        space.setPhone(req.getPhone());
        space.setStatus(req.getStatus());
        space.setRemark(req.getRemark());
        space.setExtJson(extJson);
    }

    /**
     * 校验并归一扩展属性 JSON。
     *
     * <p>它是业务自定义字段，但<b>必须校验合法性</b>：写进去一段坏 JSON 会让读取方每次解析失败，
     * 而用户已无从知道是哪一步写坏的。大小上限见 {@link SpaceConstants#MAX_EXT_JSON_BYTES}。</p>
     *
     * @param extJson 原始 JSON（可空）
     * @return 归一后的 JSON（空串归一为 null）
     */
    private String normalizeExtJson(String extJson) {
        if (!StringUtils.hasText(extJson)) {
            return null;
        }
        String trimmed = extJson.trim();
        int bytes = trimmed.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > SpaceConstants.MAX_EXT_JSON_BYTES) {
            throw new BusinessException("扩展属性过大（" + bytes + " 字节，上限 "
                + SpaceConstants.MAX_EXT_JSON_BYTES + " 字节）");
        }
        try {
            objectMapper.readTree(trimmed);
        } catch (JacksonException e) {
            log.warn("空间扩展属性不是合法 JSON：length={}", trimmed.length(), e);
            throw new BusinessException("扩展属性不是合法的 JSON");
        }
        return trimmed;
    }

    private List<SpaceResp> toRespList(List<SysSpace> spaces) {
        return toRespList(spaces, null);
    }

    /**
     * 实体列表 → 响应列表。
     *
     * @param spaces     要转换的实体（调用方已查好）
     * @param pathSource 拼路径用的全量节点快照；<b>传 {@code null} 时本方法会自己查一次</b>。
     *                   树形/列表页传进来可省掉一次**全表重复查询**（见 {@code tree()}）。
     * @return 响应列表
     */
    private List<SpaceResp> toRespList(List<SysSpace> spaces, List<SysSpace> pathSource) {
        if (spaces.isEmpty()) {
            return List.of();
        }
        List<Long> ids = spaces.stream().map(SysSpace::getId).toList();
        Map<Long, long[]> counters = loadCounters(ids);
        // 路径在内存里拼（一次取节点快照），不逐条查 CTE：树页面每行都要显示路径，
        // 逐条上溯就是 N 次递归查询。
        // 调用方已有全量快照时复用（tree() 就是这种情况：它已经查了全量，
        // 若这里再查一次就是同一张大表查两遍——数据量小时看不出来，量大时白付一份开销）。
        Map<Long, String> paths = buildPathMap(pathSource);
        List<SpaceResp> result = new ArrayList<>(spaces.size());
        for (SysSpace space : spaces) {
            SpaceResp resp = new SpaceResp();
            BeanUtils.copyProperties(space, resp);
            resp.setSpacePath(paths.get(space.getId()));
            long[] counter = counters.get(space.getId());
            resp.setChildCount(counter == null ? 0L : counter[0]);
            resp.setRelationCount(counter == null ? 0L : counter[1]);
            result.add(resp);
        }
        return result;
    }

    /**
     * 一次取全量空间节点，在内存里算出每个节点的完整路径。
     *
     * <p><b>为什么不逐条调 {@code buildSpacePath}</b>：那是每条一次递归 CTE。
     * 树页面有 N 行就要 N 次查询（N+1，架构门禁判红）。这里一次取全量、内存上溯，
     * 复杂度 O(n × 深度)，深度上限只有 6。</p>
     *
     * @return 空间 ID → 完整路径
     */
    private Map<Long, String> buildPathMap(List<SysSpace> provided) {
        List<SysSpace> all = provided != null ? provided
            : list(new LambdaQueryWrapper<SysSpace>()
                .select(SysSpace::getId, SysSpace::getPid, SysSpace::getName));
        Map<Long, SysSpace> byId = new HashMap<>(all.size());
        for (SysSpace space : all) {
            byId.put(space.getId(), space);
        }
        Map<Long, String> paths = new HashMap<>(all.size());
        for (SysSpace space : all) {
            List<String> names = new ArrayList<>();
            Long cursor = space.getId();
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
            if (!names.isEmpty()) {
                Collections.reverse(names);
                paths.put(space.getId(), String.join(PATH_SEPARATOR, names));
            }
        }
        return paths;
    }

    /**
     * 批量加载子空间数与关联数（一次分组查询）。
     *
     * @param spaceIds 空间 ID 列表（调用方保证非空）
     * @return 空间 ID → [子空间数, 关联数]
     */
    private Map<Long, long[]> loadCounters(List<Long> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = baseMapper.countChildrenAndRelations(spaceIds);
        Map<Long, long[]> counters = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long spaceId = asLongOrNull(row.get("spaceId"));
            if (spaceId == null) {
                continue;
            }
            counters.put(spaceId, new long[] {asLong(row.get("childCount")), asLong(row.get("relationCount"))});
        }
        return counters;
    }

    private static long asLong(Object value) {
        Long parsed = asLongOrNull(value);
        return parsed == null ? 0L : parsed;
    }

    private static Long asLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.warn("空间统计值无法解析为数字：value={}", value, e);
            return null;
        }
    }
}
