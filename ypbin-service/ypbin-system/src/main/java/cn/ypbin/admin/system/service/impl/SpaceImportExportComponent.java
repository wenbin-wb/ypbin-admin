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
import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.mapper.SysSpaceMapper;
import cn.ypbin.admin.system.model.req.SpaceSaveReq;
import cn.ypbin.admin.system.model.resp.SpaceImportResultResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.tools.csv.CsvDocument;
import cn.ypbin.starter.tools.csv.CsvLimits;
import cn.ypbin.starter.tools.csv.CsvRow;
import cn.ypbin.starter.tools.csv.CsvUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 空间 CSV 导入 / 导出组件。
 *
 * <p>从 {@code SysSpaceServiceImpl} 拆出：CSV 解析、字段校验、父子依赖排序、导出与模板生成都只
 * 服务于「表格通道」，与单条 CRUD / 树查询无关。写入仍由服务实现（通过 {@link SpaceImportPort}
 * 回调），组件不反向依赖服务 Bean，避免 Spring 循环引用。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
@Component
@RequiredArgsConstructor
public class SpaceImportExportComponent {

    private final SysSpaceMapper spaceMapper;

    /**
     * 导入写入端口（由 {@code SysSpaceServiceImpl} 实现）。
     */
    public interface SpaceImportPort {

        /**
         * 已加载的空间类型字典项。
         *
         * @return 允许的类型值
         */
        Set<String> allowedTypes();

        /**
         * 新建一行导入数据（编码唯一性已由导入整表索引校验）。
         *
         * @param req          保存请求
         * @param allowedTypes 已加载的类型字典
         * @param level        已计算的层级
         * @param presetId     预分配的主键（供子行解析父引用）
         * @return 新空间 ID
         */
        Long create(SpaceSaveReq req, Set<String> allowedTypes, int level, Long presetId);

        /**
         * 更新一行导入数据（upsert）。
         *
         * @param id           空间 ID
         * @param req          保存请求
         * @param allowedTypes 已加载的类型字典
         */
        void update(Long id, SpaceSaveReq req, Set<String> allowedTypes);

        /**
         * 批量新建同一层可插入的行（分块多值 INSERT，非逐行往返）。
         *
         * @param commands     待插入行（含已计算的层级与预分配主键）
         * @param allowedTypes 已加载的类型字典
         */
        void createBatch(List<SpaceCreateCmd> commands, Set<String> allowedTypes);
    }

    /**
     * 一行待批量插入的导入数据。
     *
     * @param req      保存请求（pid 已解析为真实父 ID）
     * @param level    已计算的层级
     * @param presetId 预分配的主键（子行靠它解析父引用）
     */
    public record SpaceCreateCmd(SpaceSaveReq req, int level, Long presetId) {
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

    public List<String> exportHeaders() {
        return SPACE_CSV_HEADERS;
    }

    public List<List<String>> exportRows() {
        List<SysSpace> all = spaceMapper.selectList(new LambdaQueryWrapper<SysSpace>()
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

    public List<List<String>> importTemplateRows() {
        return List.of(Arrays.asList(
            "HQ", "", "总部园区", "park", "1", "1",
            null, null, null, null, null, null, null, null, null, "示例行，可删除"));
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceImportResultResp importCsv(String content, String mode, SpaceImportPort port) {
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
        // 字典项只加载一次：此前每行 parseImportRow 与 createSpace/updateSpace 各查一次字典，
        // 5000 行导入能放大成上万次额外查询
        Set<String> allowedTypes = port.allowedTypes();
        List<ImportRow> parsed = new ArrayList<>();
        Map<String, ImportRow> byCode = new LinkedHashMap<>();
        // 一次性取「编码 → ID」与「ID → (pid, level)」快照：父链校验/层级计算全部在内存完成
        SpaceIndex index = loadSpaceIndex();
        Map<String, Long> existingIdByCode = index.idByCode();
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
                ImportRow row = parseImportRow(cells, colIndex, lineNo, allowedTypes);
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

        // ---- 第二遍：按父子依赖逐层写入 ----
        // 每轮处理「父已就绪」的行；一轮没有任何进展说明存在环或父编码不存在，
        // 剩下的行全部报错并写明原因（否则会静默丢失）。
        // 同一轮里的新增行合并成一次批量 INSERT：逐层批量既保证父先于子落库，
        // 又避免 5000 行导入变成 5000 次单行往返。
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
            List<PendingInsert> inserts = new ArrayList<>();
            for (ImportRow row : ready) {
                Long existingId = idByCode.get(row.code);
                if (existingId != null) {
                    try {
                        Long parentId = resolveParentId(row, idByCode);
                        // upsert：仍走 updateSpaceInternal（它还负责子树深度校验与 level 刷新）
                        int level = validateParentInMemory(existingId, parentId, index.nodeById());
                        applyUpsert(row, existingId, idByCode, allowedTypes, port);
                        index.nodeById().put(existingId, new long[] {parentId, level});
                        result.setUpdated(result.getUpdated() + 1);
                    } catch (BusinessException e) {
                        result.addError(row.lineNo, row.code, e.getMessage(),
                            SpaceConstants.MAX_IMPORT_ERRORS);
                    }
                    pending.remove(row);
                    continue;
                }
                try {
                    // 第一遍已用整表索引校验过编码唯一性；层级在内存算，不逐行跑 CTE
                    Long parentId = resolveParentId(row, idByCode);
                    int level = validateParentInMemory(null, parentId, index.nodeById());
                    inserts.add(new PendingInsert(row, parentId, level, IdWorker.getId()));
                } catch (BusinessException e) {
                    result.addError(row.lineNo, row.code, e.getMessage(),
                        SpaceConstants.MAX_IMPORT_ERRORS);
                    pending.remove(row);
                }
            }
            if (!inserts.isEmpty()) {
                insertRows(inserts, port, allowedTypes, idByCode, index.nodeById(), result);
                for (PendingInsert insert : inserts) {
                    pending.remove(insert.row());
                }
            }
        }
        return result;
    }

    /**
     * 批量写入本轮新增行；整批撞唯一键时退回逐行写，把冲突归因到具体行。
     *
     * <p>逐行重试只发生在批量失败之后：并发导入同一份文件才会撞
     * {@code uk_space_tenant_code}，此时宁可多几次往返，也要给出「哪一行编码重复」，
     * 而不是让 {@link DuplicateKeyException} 冒泡触发整个事务回滚。</p>
     *
     * @param inserts      本轮待插入行
     * @param port         写入端口
     * @param allowedTypes 已加载的类型字典
     * @param idByCode     编码 → 空间 ID 映射（成功后回写）
     * @param nodeById     空间 ID → (父 ID, 层级) 快照（成功后回写）
     * @param result       导入结果（累计插入数与错误）
     */
    private void insertRows(List<PendingInsert> inserts, SpaceImportPort port,
            Set<String> allowedTypes, Map<String, Long> idByCode,
            Map<Long, long[]> nodeById, SpaceImportResultResp result) {
        try {
            port.createBatch(inserts.stream().map(PendingInsert::cmd).toList(), allowedTypes);
            for (PendingInsert insert : inserts) {
                acceptInsert(insert, idByCode, nodeById, result);
            }
        } catch (DuplicateKeyException e) {
            for (PendingInsert insert : inserts) {
                try {
                    port.create(insert.cmd().req(), allowedTypes, insert.cmd().level(),
                        insert.cmd().presetId());
                    acceptInsert(insert, idByCode, nodeById, result);
                } catch (DuplicateKeyException ex) {
                    result.addError(insert.row().lineNo, insert.row().code,
                        "编码已存在（数据库唯一约束冲突，可能有并发导入）",
                        SpaceConstants.MAX_IMPORT_ERRORS);
                } catch (BusinessException ex) {
                    result.addError(insert.row().lineNo, insert.row().code, ex.getMessage(),
                        SpaceConstants.MAX_IMPORT_ERRORS);
                }
            }
        }
    }

    /**
     * 记录一行插入成功后的编码映射与层级快照。
     *
     * @param insert   已插入行
     * @param idByCode 编码 → 空间 ID 映射
     * @param nodeById 空间 ID → (父 ID, 层级) 快照
     * @param result   导入结果
     */
    private void acceptInsert(PendingInsert insert, Map<String, Long> idByCode,
            Map<Long, long[]> nodeById, SpaceImportResultResp result) {
        idByCode.put(insert.row().code, insert.cmd().presetId());
        nodeById.put(insert.cmd().presetId(),
            new long[] {insert.parentId(), insert.cmd().level()});
        result.setInserted(result.getInserted() + 1);
    }

    /**
     * 本轮待插入的一行（父 ID、层级、预分配主键已算好）。
     *
     * @param row      导入行
     * @param parentId 已解析的父空间 ID
     * @param level    已计算的层级
     * @param newId    预分配的主键
     */
    private record PendingInsert(ImportRow row, Long parentId, int level, Long newId) {

        /**
         * 转成端口批量写入命令。
         *
         * @return 写入命令
         */
        private SpaceCreateCmd cmd() {
            return new SpaceCreateCmd(row.toSaveReq(parentId), level, newId);
        }
    }

    /**
     * upsert 模式：按编码更新已存在的空间。
     *
     * <p><b>整行覆盖</b>（与 `updateSpace` 一致）：CSV 一行就是一份完整的空间定义。
     * 若只更新部分列，用户会误以为「表格里的空值表示保持不变」，而实际上空值会清空字段。</p>
     *
     * @param row          导入行
     * @param id           已存在的空间 ID
     * @param idByCode     编码 → 空间 ID 映射（含本次已插入的行）
     * @param allowedTypes 已加载的空间类型允许值
     * @throws BusinessException 父编码无法解析
     */
    private void applyUpsert(ImportRow row, Long id, Map<String, Long> idByCode,
            Set<String> allowedTypes, SpaceImportPort port) {
        port.update(id, row.toSaveReq(resolveParentId(row, idByCode)), allowedTypes);
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
     * 载入导入所需的租户空间索引（一次查询）：编码 → ID、ID → (pid, level)。
     *
     * @return 空间索引
     */
    private SpaceIndex loadSpaceIndex() {
        List<SysSpace> all = spaceMapper.selectList(new LambdaQueryWrapper<SysSpace>()
            .select(SysSpace::getId, SysSpace::getCode, SysSpace::getPid, SysSpace::getLevel));
        Map<String, Long> idByCode = new HashMap<>(all.size());
        Map<Long, long[]> nodeById = new HashMap<>(all.size());
        for (SysSpace space : all) {
            if (space.getCode() != null) {
                idByCode.put(space.getCode(), space.getId());
            }
            nodeById.put(space.getId(), new long[] {
                space.getPid() == null ? SpaceConstants.ROOT_PARENT_ID : space.getPid(),
                space.getLevel() == null ? 1 : space.getLevel()});
        }
        return new SpaceIndex(idByCode, nodeById);
    }

    /**
     * 在内存快照上校验父链并计算层级（导入路径，替代逐行递归 CTE）。
     *
     * <p>与 {@link #validateParent} 的判定口径一致：父必须存在、不能成环、不能挂到自身后代、
     * 链尾必须到根、总深度不超过上限。导入的整表快照已在调用前一次性加载。</p>
     *
     * @param selfId   当前空间 ID（新建为 null）
     * @param parentId 归一后的父空间 ID（0=根）
     * @param nodeById ID → (pid, level) 快照（含本次已插入的新行）
     * @return 该空间应处的层级
     */
    private int validateParentInMemory(Long selfId, long parentId, Map<Long, long[]> nodeById) {
        if (parentId == SpaceConstants.ROOT_PARENT_ID) {
            return 1;
        }
        if (selfId != null && selfId.longValue() == parentId) {
            throw new BusinessException("空间不能作为自己的父空间");
        }
        int depth = 0;
        long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != SpaceConstants.ROOT_PARENT_ID) {
            if (!visited.add(cursor)) {
                throw new BusinessException(
                    "空间层级数据异常（父子关系成环或层级过深）：请检查空间树的父空间配置");
            }
            if (selfId != null && selfId.longValue() == cursor) {
                throw new BusinessException("空间不能挂到自己的后代下（父链上出现了当前空间本身）");
            }
            long[] node = nodeById.get(cursor);
            if (node == null) {
                throw new BusinessException("父空间不存在：" + cursor);
            }
            cursor = node[0];
            depth++;
            if (depth > SpaceConstants.MAX_SPACE_DEPTH) {
                throw new BusinessException("空间层级不能超过 " + SpaceConstants.MAX_SPACE_DEPTH
                    + " 层（当前将超过上限）");
            }
        }
        int newDepth = depth + 1;
        if (newDepth > SpaceConstants.MAX_SPACE_DEPTH) {
            throw new BusinessException("空间层级不能超过 " + SpaceConstants.MAX_SPACE_DEPTH
                + " 层（当前将达第 " + newDepth + " 层）");
        }
        return newDepth;
    }

    /**
     * 导入用的空间索引快照。
     *
     * @param idByCode 编码 → 空间 ID
     * @param nodeById 空间 ID → (pid, level)
     */
    private record SpaceIndex(Map<String, Long> idByCode, Map<Long, long[]> nodeById) {
    }

    /**
     * 解析并校验一行导入数据。
     *
     * @param cells        该行单元格
     * @param colIndex     列名 → 列下标
     * @param lineNo       物理行号（用于报错定位）
     * @param allowedTypes 已加载的空间类型允许值
     * @return 导入行
     * @throws BusinessException 该行格式或取值不合法
     */
    private ImportRow parseImportRow(List<String> cells, Map<String, Integer> colIndex, int lineNo,
            Set<String> allowedTypes) {
        ImportRow row = new ImportRow();
        row.lineNo = lineNo;
        row.code = requireCell(cells, colIndex, "code", SpaceConstants.MAX_CODE_LENGTH);
        // 与单条 API 的 @Pattern 同一判据：导入不能成为绕过编码格式的后门
        if (!row.code.matches(SpaceConstants.CODE_PATTERN)) {
            throw new BusinessException("空间编码只能包含字母、数字、下划线与连字符");
        }
        row.name = requireCell(cells, colIndex, "name", SpaceConstants.MAX_NAME_LENGTH);
        row.spaceType = requireCell(cells, colIndex, "spaceType",
            SpaceConstants.MAX_SPACE_TYPE_LENGTH);
        // 类型必须是字典已登记的项值（与 createSpace 同一判据，不另立一套）
        requireSpaceType(row.spaceType, allowedTypes);
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
        // 与 SpaceSaveReq 的 Bean Validation 同口径：导入通道不能绕过数值范围
        if (row.status != null && row.status != 0 && row.status != 1) {
            throw new BusinessException("状态只能为 0 或 1");
        }
        if (row.longitude != null && (row.longitude.compareTo(BigDecimal.valueOf(-180)) < 0
            || row.longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException("经度范围 -180 ~ 180");
        }
        if (row.latitude != null && (row.latitude.compareTo(BigDecimal.valueOf(-90)) < 0
            || row.latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException("纬度范围 -90 ~ 90");
        }
        if (row.area != null && row.area.signum() < 0) {
            throw new BusinessException("面积不能为负");
        }
        if (row.capacity != null && row.capacity < 0) {
            throw new BusinessException("容量不能为负");
        }
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


    /**
     * 空间类型必须是字典登记值（与单条 API 同一判据）。
     *
     * @param spaceType    空间类型
     * @param allowedTypes 已加载的允许值
     */
    private void requireSpaceType(String spaceType, Set<String> allowedTypes) {
        if (!StringUtils.hasText(spaceType)) {
            throw new BusinessException("空间类型不能为空");
        }
        if (allowedTypes != null && !allowedTypes.contains(spaceType.trim())) {
            throw new BusinessException("空间类型不合法：" + spaceType
                + "（允许值见字典 space_type，新增类型请先在字典里加项）");
        }
    }
}
