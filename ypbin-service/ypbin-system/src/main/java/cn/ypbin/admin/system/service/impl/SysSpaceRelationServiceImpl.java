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
import cn.ypbin.admin.system.model.query.SpaceRelationQuery;
import cn.ypbin.admin.system.model.req.SpaceBindReq;
import cn.ypbin.admin.system.model.req.SpaceSyncReq;
import cn.ypbin.admin.system.model.resp.SpaceRelationResp;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 空间关联服务实现。
 *
 * <p><b>批量写在一次事务里</b>：绑定 20 台设备要么全成功、要么全不生效。
 * 逐条独立提交会让「第 13 条失败」留下半绑定状态，而用户看到的是「操作失败」。</p>
 *
 * <p><b>不许循环内查库</b>：所有「按 ID 批量取」都走 {@code in(...)}，且 {@code in} 前显式判空
 * （空集合进 {@code IN} 会生成 {@code IN ()} 语法错误）。这两条都有架构门禁兜底。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class SysSpaceRelationServiceImpl extends BaseServiceImpl<SysSpaceRelationMapper, SysSpaceRelation>
    implements SysSpaceRelationService {

    /** 关联对象类型字典编码（与 002-data.sql 的 sys_dict.code 一致）。 */
    private static final String DICT_TARGET_TYPE = "space_target_type";

    private final SysSpaceMapper spaceMapper;

    private final SysDictItemMapper dictItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bind(Long spaceId, SpaceBindReq req) {
        SysSpace space = requireSpace(spaceId);
        String targetType = normalizeTargetType(req.getTargetType());
        List<Long> targetIds = distinctIds(req.getTargetIds());
        if (targetIds.isEmpty()) {
            throw new BusinessException("请至少选择一个关联对象");
        }
        if (targetIds.size() > SpaceConstants.MAX_BATCH_TARGETS) {
            throw new BusinessException("单次最多绑定 " + SpaceConstants.MAX_BATCH_TARGETS + " 个对象");
        }
        int isPrimary = req.getIsPrimary() == null ? SpaceConstants.PRIMARY_NO : req.getIsPrimary();
        // 主空间最多一个：先把该对象其它关联的主标记清掉（一次 UPDATE，不逐条改）
        if (isPrimary == SpaceConstants.PRIMARY_YES) {
            clearPrimaryFlag(targetType, targetIds, space.getId());
        }
        // 清空其它空间下的同类关联（换位置场景）
        if (Boolean.TRUE.equals(req.getClearOthers())) {
            removeRelationsExceptSpace(targetType, targetIds, space.getId());
        }
        // 已存在的关联：更新主标记/备注（幂等重复提交）；不存在：新增
        List<SysSpaceRelation> existing = list(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getSpaceId, space.getId())
            .eq(SysSpaceRelation::getTargetType, targetType)
            .in(SysSpaceRelation::getTargetId, targetIds));
        Map<Long, SysSpaceRelation> existingByTarget = new HashMap<>(existing.size());
        for (SysSpaceRelation relation : existing) {
            existingByTarget.put(relation.getTargetId(), relation);
        }
        // **批量写，不在循环里逐条 save/updateById**：逐条往返是 N+1（架构门禁判红），
        // 且 20 条各自一个事务会让「第 13 条失败」留下半绑定状态。
        // 这里先分拣「新增」与「需更新」两批，各自一次批量调用。
        List<SysSpaceRelation> toInsert = new ArrayList<>();
        List<SysSpaceRelation> toUpdate = new ArrayList<>();
        for (Long targetId : targetIds) {
            SysSpaceRelation relation = existingByTarget.get(targetId);
            if (relation == null) {
                relation = new SysSpaceRelation();
                relation.setSpaceId(space.getId());
                relation.setTargetType(targetType);
                relation.setTargetId(targetId);
                relation.setIsPrimary(isPrimary);
                relation.setRemark(req.getRemark());
                relation.setStatus(1);
                toInsert.add(relation);
                continue;
            }
            relation.setIsPrimary(isPrimary);
            relation.setRemark(req.getRemark());
            toUpdate.add(relation);
        }
        boolean inserted = toInsert.isEmpty() || saveBatch(toInsert);
        boolean updated = toUpdate.isEmpty() || updateBatchById(toUpdate);
        if (!inserted || !updated) {
            // 明确失败，不静默当作部分成功：调用方以为绑了、实际没绑是全数据链路最坏的形态
            throw new BusinessException("空间绑定失败（新增 " + toInsert.size()
                + " 条、更新 " + toUpdate.size() + " 条）");
        }
        // 主空间兜底：clearOthers=true 时可能把原主空间删掉（如设备挪位置），
        // 若不补一个，「主空间」查询会返回空，依赖主空间做定位的页面拿不到数据。
        ensurePrimarySpaceExists(targetType, targetIds);
        return toInsert.size() + toUpdate.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbind(Long spaceId, String targetType, List<Long> targetIds) {
        requireSpace(spaceId);
        String normalizedType = normalizeTargetType(targetType);
        List<Long> ids = distinctIds(targetIds);
        if (ids.isEmpty()) {
            throw new BusinessException("请至少选择一个关联对象");
        }
        // **物理删**（不是逻辑删）：本表是纯关联表，关联行没有保留价值；
        // 而逻辑删会让唯一键 uk_space_relation 挡住「解绑后重新绑定」——实测 1062，
        // 表现为「解绑一次 ⇒ 该 (空间,对象) 组合永久不可再绑」。
        return baseMapper.physicalDelete(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getSpaceId, spaceId)
            .eq(SysSpaceRelation::getTargetType, normalizedType)
            .in(SysSpaceRelation::getTargetId, ids));
    }

    @Override
    public List<SpaceRelationResp> listByTarget(SpaceRelationQuery query) {
        if (query == null || !StringUtils.hasText(query.getTargetType()) || query.getTargetId() == null) {
            throw new BusinessException("反查必须指定关联对象类型与对象 ID");
        }
        String targetType = normalizeTargetType(query.getTargetType());
        List<SysSpaceRelation> relations = list(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType)
            .eq(SysSpaceRelation::getTargetId, query.getTargetId())
            .eq(Boolean.TRUE.equals(query.getPrimaryOnly()), SysSpaceRelation::getIsPrimary,
                SpaceConstants.PRIMARY_YES)
            .orderByDesc(SysSpaceRelation::getIsPrimary)
            .orderByAsc(SysSpaceRelation::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        // 一次批量取空间（不在循环里查）
        List<Long> spaceIds = relations.stream().map(SysSpaceRelation::getSpaceId).distinct().toList();
        List<SysSpace> spaces = spaceMapper.selectByIds(spaceIds);
        Map<Long, SysSpace> spaceById = new HashMap<>(spaces.size());
        for (SysSpace space : spaces) {
            spaceById.put(space.getId(), space);
        }
        List<SpaceRelationResp> result = new ArrayList<>(relations.size());
        for (SysSpaceRelation relation : relations) {
            SysSpace space = spaceById.get(relation.getSpaceId());
            SpaceRelationResp resp = new SpaceRelationResp();
            resp.setId(relation.getId());
            resp.setSpaceId(relation.getSpaceId());
            resp.setTargetType(relation.getTargetType());
            resp.setTargetId(relation.getTargetId());
            resp.setIsPrimary(relation.getIsPrimary());
            resp.setRemark(relation.getRemark());
            resp.setStatus(relation.getStatus());
            resp.setCreateTime(relation.getCreateTime());
            if (space != null) {
                resp.setSpaceName(space.getName());
                resp.setSpaceCode(space.getCode());
                resp.setSpaceType(space.getSpaceType());
                }
            result.add(resp);
        }
        // 完整路径要沿父链逐条上溯：改为一次性批量取全量空间后在内存拼（避免 N 次 CTE）
        fillSpacePaths(result);
        return result;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncTargetSpaces(SpaceSyncReq req) {
        String targetType = normalizeTargetType(req.getTargetType());
        Long targetId = req.getTargetId();
        if (targetId == null) {
            throw new BusinessException("关联对象 ID 不能为空");
        }
        List<Long> targetSpaceIds = req.getSpaceIds() == null
            ? List.of() : distinctIds(req.getSpaceIds());
        if (targetSpaceIds.size() > SpaceConstants.MAX_BATCH_TARGETS) {
            throw new BusinessException("单次最多关联 " + SpaceConstants.MAX_BATCH_TARGETS + " 个空间");
        }
        // 目标空间必须全部真实存在且属于本租户（一次批量查，不逐个 selectById）
        if (!targetSpaceIds.isEmpty()) {
            List<SysSpace> spaces = spaceMapper.selectByIds(targetSpaceIds);
            if (spaces.size() != targetSpaceIds.size()) {
                // 差集给出具体缺失的 ID：只报「空间不存在」会让用户不知道是哪一个
                List<Long> missing = new ArrayList<>(targetSpaceIds);
                for (SysSpace space : spaces) {
                    missing.remove(space.getId());
                }
                throw new BusinessException("目标空间不存在或无权操作：" + missing);
            }
        }
        // 现有全部关联（一次查）
        List<SysSpaceRelation> existing = list(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType)
            .eq(SysSpaceRelation::getTargetId, targetId));
        Set<Long> existingSpaceIds = new LinkedHashSet<>();
        for (SysSpaceRelation relation : existing) {
            existingSpaceIds.add(relation.getSpaceId());
        }
        Set<Long> wanted = new LinkedHashSet<>(targetSpaceIds);
        // 差集：要删的 = 现有 - 目标；要加的 = 目标 - 现有
        List<Long> toRemove = new ArrayList<>();
        for (Long spaceId : existingSpaceIds) {
            if (!wanted.contains(spaceId)) {
                toRemove.add(spaceId);
            }
        }
        List<SysSpaceRelation> toInsert = new ArrayList<>();
        for (Long spaceId : wanted) {
            if (!existingSpaceIds.contains(spaceId)) {
                SysSpaceRelation relation = new SysSpaceRelation();
                relation.setSpaceId(spaceId);
                relation.setTargetType(targetType);
                relation.setTargetId(targetId);
                relation.setIsPrimary(SpaceConstants.PRIMARY_NO);
                relation.setStatus(1);
                toInsert.add(relation);
            }
        }
        // 批量删（物理删，理由同 unbind：逻辑删会撞唯一键让解绑后无法重绑）
        if (!toRemove.isEmpty()) {
            baseMapper.physicalDelete(new LambdaQueryWrapper<SysSpaceRelation>()
                .eq(SysSpaceRelation::getTargetType, targetType)
                .eq(SysSpaceRelation::getTargetId, targetId)
                .in(SysSpaceRelation::getSpaceId, toRemove));
        }
        if (!toInsert.isEmpty() && !saveBatch(toInsert)) {
            throw new BusinessException("空间关联保存失败（新增 " + toInsert.size() + " 条）");
        }
        // 主空间兜底：若原主空间被移除，剩余里挑一个设为主（否则该对象会「没有任何主空间」）
        ensurePrimarySpaceExists(targetType, targetId);
        return toRemove.size() + toInsert.size();
    }

    /**
     * 保证某对象在存在关联时**至少有一个主空间**。
     *
     * <p>为什么需要：主空间被移除后若没有替补，前端「主空间」标签会整块消失，
     * 依赖主空间做定位的业务（设备详情显示位置）会拿不到数据。这里按「取最早一条」兜底，
     * 并**只在本对象当前没有任何主空间时**才动——不覆盖用户显式设置的主空间。</p>
     *
     * @param targetType 关联对象类型
     * @param targetId   关联对象 ID
     */
    private void ensurePrimarySpaceExists(String targetType, Long targetId) {
        ensurePrimarySpaceExists(targetType, List.of(targetId));
    }

    /**
     * 批量保证「每个对象在其有空间关联时至少一个主空间」。
     *
     * <p><b>为什么批量</b>：bind 可能一次传 500 个 targetIds，逐对象查库就是 500 次查询
     * （N+1，架构门禁判红）。这里一次 in 查询取回全部关联，内存分组判定。</p>
     *
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表（调用方保证非空）
     */
    private void ensurePrimarySpaceExists(String targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        List<SysSpaceRelation> all = list(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType)
            .in(SysSpaceRelation::getTargetId, targetIds)
            .orderByAsc(SysSpaceRelation::getId));
        if (all.isEmpty()) {
            return;
        }
        // 按对象分组，记住「谁还没有主空间」及其第一个关联
        Map<Long, SysSpaceRelation> fallbackByTarget = new LinkedHashMap<>();
        Set<Long> hasPrimary = new HashSet<>();
        for (SysSpaceRelation relation : all) {
            if (relation.getIsPrimary() != null
                && relation.getIsPrimary() == SpaceConstants.PRIMARY_YES) {
                hasPrimary.add(relation.getTargetId());
                continue;
            }
            fallbackByTarget.putIfAbsent(relation.getTargetId(), relation);
        }
        List<SysSpaceRelation> toPromote = new ArrayList<>();
        for (Map.Entry<Long, SysSpaceRelation> entry : fallbackByTarget.entrySet()) {
            if (!hasPrimary.contains(entry.getKey())) {
                entry.getValue().setIsPrimary(SpaceConstants.PRIMARY_YES);
                toPromote.add(entry.getValue());
            }
        }
        if (!toPromote.isEmpty()) {
            updateBatchById(toPromote);
        }
    }


    @Override
    public List<SpaceRelationResp> listBySpace(Long spaceId, String targetType) {
        requireSpace(spaceId);
        LambdaQueryWrapper<SysSpaceRelation> wrapper = new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getSpaceId, spaceId)
            .orderByDesc(SysSpaceRelation::getIsPrimary)
            .orderByAsc(SysSpaceRelation::getId);
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(SysSpaceRelation::getTargetType, targetType.trim());
        }
        List<SysSpaceRelation> relations = list(wrapper);
        if (relations.isEmpty()) {
            return List.of();
        }
        // 空间信息只需查一次（同一 spaceId）
        SysSpace space = spaceMapper.selectById(spaceId);
        List<SpaceRelationResp> result = new ArrayList<>(relations.size());
        for (SysSpaceRelation relation : relations) {
            SpaceRelationResp resp = new SpaceRelationResp();
            resp.setId(relation.getId());
            resp.setSpaceId(relation.getSpaceId());
            resp.setTargetType(relation.getTargetType());
            resp.setTargetId(relation.getTargetId());
            resp.setIsPrimary(relation.getIsPrimary());
            resp.setRemark(relation.getRemark());
            resp.setStatus(relation.getStatus());
            resp.setCreateTime(relation.getCreateTime());
            if (space != null) {
                resp.setSpaceName(space.getName());
                resp.setSpaceCode(space.getCode());
                resp.setSpaceType(space.getSpaceType());
                }
            result.add(resp);
        }
        fillSpacePaths(result);
        return result;
    }

    @Override
    public Map<Long, List<SpaceRelationResp>> listByTargets(String targetType,
        List<Long> targetIds) {
        if (!StringUtils.hasText(targetType)) {
            throw new BusinessException("关联对象类型不能为空");
        }
        String normalizedType = normalizeTargetType(targetType);
        List<Long> ids = distinctIds(targetIds);
        if (ids.isEmpty()) {
            // 空集合直接返回空 Map：查库无意义，且空 IN () 是 SQL 语法错
            return Map.of();
        }
        // 查询次数恒为 2：① 一次 IN 取全部关联；② 一次批量取空间信息（fillSpacePaths 内部）。
        // 不逐对象调 listByTarget —— 那是 N+1。
        List<SysSpaceRelation> relations = list(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, normalizedType)
            .in(SysSpaceRelation::getTargetId, ids)
            .orderByDesc(SysSpaceRelation::getIsPrimary)
            .orderByAsc(SysSpaceRelation::getId));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> spaceIds = relations.stream().map(SysSpaceRelation::getSpaceId).distinct().toList();
        List<SysSpace> spaces = spaceMapper.selectByIds(spaceIds);
        Map<Long, SysSpace> spaceById = new HashMap<>(spaces.size());
        for (SysSpace space : spaces) {
            spaceById.put(space.getId(), space);
        }
        // 按对象分组（LinkedHashMap 保持稳定顺序，便于前端渲染）
        Map<Long, List<SpaceRelationResp>> grouped = new LinkedHashMap<>();
        List<SpaceRelationResp> all = new ArrayList<>(relations.size());
        for (SysSpaceRelation relation : relations) {
            SpaceRelationResp resp = toRelationResp(relation,
                spaceById.get(relation.getSpaceId()));
            grouped.computeIfAbsent(relation.getTargetId(), key -> new ArrayList<>()).add(resp);
            all.add(resp);
        }
        // 路径一次性批量填充（内部取全量空间在内存拼，不逐条 CTE）
        fillSpacePaths(all);
        return grouped;
    }

    /**
     * 关联实体 → 响应（含空间侧字段）。
     *
     * <p>抽出来是因为 {@code listByTarget} / {@code listBySpace} / {@code listByTargets}
     * 三处都要做同一件事；复制三份会在加字段时漏改其中一处。</p>
     *
     * <p>空间类型的展示文本由 {@code SpaceRelationResp.spaceType} 上的 {@code @DictText} 在序列化时
     * 输出 {@code spaceTypeText}，<b>不在这里手工翻译</b>（否则与 starter 形成两份实现）。</p>
     *
     * @param relation 关联实体
     * @param space    空间实体（可为 null：空间被删但关联残留）
     * @return 关联响应
     */
    private SpaceRelationResp toRelationResp(SysSpaceRelation relation, SysSpace space) {
        SpaceRelationResp resp = new SpaceRelationResp();
        resp.setId(relation.getId());
        resp.setSpaceId(relation.getSpaceId());
        resp.setTargetType(relation.getTargetType());
        resp.setTargetId(relation.getTargetId());
        resp.setIsPrimary(relation.getIsPrimary());
        resp.setRemark(relation.getRemark());
        resp.setStatus(relation.getStatus());
        resp.setCreateTime(relation.getCreateTime());
        if (space != null) {
            resp.setSpaceName(space.getName());
            resp.setSpaceCode(space.getCode());
            resp.setSpaceType(space.getSpaceType());
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeByTarget(String targetType, Long targetId) {
        if (!StringUtils.hasText(targetType) || targetId == null) {
            throw new BusinessException("删除空间关联必须指定关联对象类型与对象 ID");
        }
        // 物理删，理由同 unbind
        return baseMapper.physicalDelete(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType.trim())
            .eq(SysSpaceRelation::getTargetId, targetId));
    }

    // ---------- 内部 ----------

    /**
     * 批量填充空间完整路径（如「总部园区 / A 栋 / 1F / 101 室」）。
     *
     * <p><b>为什么不在循环里调 {@code buildSpacePath}</b>：那是每条关联一次递归 CTE，
     * 20 个空间就是 20 次查询（N+1，门禁判红）。这里一次性取全量空间，在内存里沿 pid 上溯拼路径。</p>
     *
     * @param items 关联响应列表
     */
    private void fillSpacePaths(List<SpaceRelationResp> items) {
        List<SysSpace> allSpaces = spaceMapper.selectList(new LambdaQueryWrapper<SysSpace>()
            .select(SysSpace::getId, SysSpace::getPid, SysSpace::getName));
        Map<Long, SysSpace> byId = new HashMap<>(allSpaces.size());
        for (SysSpace space : allSpaces) {
            byId.put(space.getId(), space);
        }
        for (SpaceRelationResp item : items) {
            List<String> names = new ArrayList<>();
            Long cursor = item.getSpaceId();
            // 上溯有界：最多走 MAX_SPACE_DEPTH 层，防历史脏数据成环导致死循环
            int guard = 0;
            while (cursor != null && cursor != SpaceConstants.ROOT_PARENT_ID
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
                item.setSpacePath(String.join(" / ", names));
            }
        }
    }

    private void clearPrimaryFlag(String targetType, List<Long> targetIds, Long keepSpaceId) {
        baseMapper.update(null, new LambdaUpdateWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType)
            .in(SysSpaceRelation::getTargetId, targetIds)
            .ne(SysSpaceRelation::getSpaceId, keepSpaceId)
            .eq(SysSpaceRelation::getIsPrimary, SpaceConstants.PRIMARY_YES)
            .set(SysSpaceRelation::getIsPrimary, SpaceConstants.PRIMARY_NO));
    }

    private void removeRelationsExceptSpace(String targetType, List<Long> targetIds, Long keepSpaceId) {
        // 物理删，理由同 unbind：逻辑删会触发唯一键冲突，让「挪回原空间」永久失败
        baseMapper.physicalDelete(new LambdaQueryWrapper<SysSpaceRelation>()
            .eq(SysSpaceRelation::getTargetType, targetType)
            .in(SysSpaceRelation::getTargetId, targetIds)
            .ne(SysSpaceRelation::getSpaceId, keepSpaceId));
    }

    private SysSpace requireSpace(Long spaceId) {
        if (spaceId == null) {
            throw new BusinessException("空间 ID 不能为空");
        }
        SysSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new BusinessException("空间不存在或无权操作");
        }
        return space;
    }

    /**
     * 校验关联对象类型必须是字典里登记过的值。
     *
     * <p><b>为什么要校验</b>：{@code target_type} 是自由字符串列，若接受任意值，
     * 库里会出现 {@code "dept "}、{@code "DEPT"}、{@code "department"} 三种写法指向同一业务，
     * 反查时互相查不到。字典是取值的单一来源。</p>
     *
     * @param targetType 原始类型
     * @return 去空白后的类型
     */
    private String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw new BusinessException("关联对象类型不能为空");
        }
        String trimmed = targetType.trim();
        List<SysDictItem> items = dictItemMapper.selectByDictCode(DICT_TARGET_TYPE);
        for (SysDictItem item : items) {
            if (trimmed.equals(item.getValue())) {
                return trimmed;
            }
        }
        throw new BusinessException("关联对象类型不合法：" + trimmed
            + "（允许值见字典 space_target_type）");
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ids.size());
        for (Long id : ids) {
            if (id == null) {
                throw new BusinessException("关联对象 ID 不能为空");
            }
            unique.add(id);
        }
        return new ArrayList<>(unique);
    }
}
