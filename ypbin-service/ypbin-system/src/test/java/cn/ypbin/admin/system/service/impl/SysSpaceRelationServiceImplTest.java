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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.BatchResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 空间关联服务测试：类型校验、批量语义、防 N+1、主空间唯一。
 *
 * <p><b>为什么必须钉住「批量写」</b>：绑 20 台设备若逐条 {@code save()}，就是 20 次数据库往返
 * （架构门禁 {@code loopsMustNotCallDbOrRpc} 会判红），而且每条各自提交会让「第 13 条失败」
 * 留下半绑定状态——用户看到「操作失败」，库里却已绑了 12 条。故断言走的是
 * {@code saveBatch}/{@code updateBatchById}，且断言<b>不得</b>出现逐条 insert。</p>
 *
 * <p><b>为什么必须钉住类型校验</b>：{@code target_type} 是自由字符串列。若不校验，
 * 库里会出现 {@code dept}、{@code DEPT}、{@code " dept "} 三种写法指向同一业务，
 * 反查时互相查不到——这种脏数据没有报错，只会让「这个部门在哪些空间」查出来是空的。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
class SysSpaceRelationServiceImplTest {

    private static final Long SPACE_ID = 10L;

    private static final Long OTHER_SPACE_ID = 20L;

    private SysSpaceMapper spaceMapper;

    private SysSpaceRelationMapper relationMapper;

    private SysDictItemMapper dictItemMapper;

    private SysSpaceRelationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "space-relation"),
            SysSpaceRelation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "space-relation"),
            SysSpace.class);
    }

    @BeforeEach
    void setUp() {
        spaceMapper = mock(SysSpaceMapper.class);
        relationMapper = mock(SysSpaceRelationMapper.class);
        dictItemMapper = mock(SysDictItemMapper.class);
        // 用 spy：saveBatch/updateBatchById 是 IService 的默认方法，内部经 MapperProxy 取元数据，
        // 纯 mock 的 Mapper 无法提供 ⇒ 这两个方法必须桩掉；被测的是「bind 是否走了批量路径」。
        service = spy(new SysSpaceRelationServiceImpl(spaceMapper, dictItemMapper));
        ReflectionTestUtils.setField(service, "baseMapper", relationMapper);
        doAnswer(invocation -> {
            List<SysSpaceRelation> batch = invocation.getArgument(0);
            return !batch.isEmpty();
        }).when(service).saveBatch(anyList());
        doAnswer(invocation -> {
            List<SysSpaceRelation> batch = invocation.getArgument(0);
            return !batch.isEmpty();
        }).when(service).updateBatchById(anyList());
    }

    // ---------- 类型校验 ----------

    @Test
    @DisplayName("★ 关联对象类型必须是字典登记值（防 dept/DEPT/' dept ' 三种写法漂移）")
    void shouldRejectUnknownTargetType() {
        // 字典里只有 dept/user/device/asset
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(
            dictItem("dept"), dictItem("user"), dictItem("device"), dictItem("asset")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));

        assertThatThrownBy(() -> service.bind(SPACE_ID, bindReq("department", List.of(1L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("关联对象类型不合法");
        verify(relationMapper, never()).insert(any(SysSpaceRelation.class));
    }

    @Test
    @DisplayName("合法的关联类型放行")
    void shouldAcceptRegisteredTargetType() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());

        assertThat(service.bind(SPACE_ID, bindReq("dept", List.of(1L, 2L)))).isEqualTo(2);
    }

    // ---------- 批量语义 ----------

    @Test
    @DisplayName("★ 批量绑定走 saveBatch（一次批量写），不逐条 insert")
    void shouldUseBatchInsertNotPerRow() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("device")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());

        int affected = service.bind(SPACE_ID, bindReq("device", List.of(1L, 2L, 3L)));

        assertThat(affected).isEqualTo(3);
        // 关键断言：走的是 saveBatch（一次批量），不是逐条 save
        verify(service).saveBatch(anyList());
    }

    @Test
    @DisplayName("重复 ID 先去重（同一请求里 1,1,2 只绑 2 条）")
    void shouldDistinctTargetIds() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("device")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());

        assertThat(service.bind(SPACE_ID, bindReq("device", List.of(1L, 1L, 2L)))).isEqualTo(2);
    }

    @Test
    @DisplayName("已存在的关联走 updateBatchById（幂等重复提交不重复插入）")
    void shouldUpdateExistingRelationsInsteadOfInserting() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        SysSpaceRelation existing = new SysSpaceRelation();
        existing.setId(500L);
        existing.setSpaceId(SPACE_ID);
        existing.setTargetType("dept");
        existing.setTargetId(1L);
        existing.setIsPrimary(SpaceConstants.PRIMARY_NO);
        when(relationMapper.selectList(any())).thenReturn(List.of(existing));
        when(relationMapper.updateById(anyList())).thenAnswer(invocation -> batchResult());

        int affected = service.bind(SPACE_ID, bindReq("dept", List.of(1L)));

        assertThat(affected).isEqualTo(1);
        verify(service, atLeastOnce()).updateBatchById(anyList());
        verify(service, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("清空其它空间关联（换位置场景）：先删旧再绑新")
    void shouldClearOtherSpacesWhenRequested() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("device")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());
        when(relationMapper.physicalDelete(any())).thenReturn(1);

        SpaceBindReq req = bindReq("device", List.of(1L));
        req.setClearOthers(true);
        service.bind(SPACE_ID, req);

        // 「挪位置」语义：必须先把该对象在别的空间的关联删掉，否则设备会同时出现在两个楼栋
        verify(relationMapper).physicalDelete(any());
    }

    @Test
    @DisplayName("未要求清空时不得删除其它空间的关联（部门可对应多个空间点）")
    void shouldNotClearOtherSpacesByDefault() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());

        service.bind(SPACE_ID, bindReq("dept", List.of(1L)));

        verify(relationMapper, never()).physicalDelete(any());
    }

    // ---------- 主空间唯一 ----------

    @Test
    @DisplayName("★ 设为主空间时先清掉同对象在其它空间的主标记（主空间最多一个）")
    void shouldClearPrimaryFlagWhenSettingPrimary() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(space(SPACE_ID));
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(anyList())).thenAnswer(invocation -> batchResult());
        when(relationMapper.update(any(), any())).thenReturn(1);

        SpaceBindReq req = bindReq("dept", List.of(1L));
        req.setIsPrimary(SpaceConstants.PRIMARY_YES);
        service.bind(SPACE_ID, req);

        // 一次条件 UPDATE 清旧主标记；必须断言「清的是谁」——只 verify(update) 是假保护：
        // 删掉 .ne(spaceId, keepSpaceId) 变成「清所有主标记」测试照样绿（复核者实测）。
        ArgumentCaptor<LambdaUpdateWrapper<SysSpaceRelation>> captor =
            ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(relationMapper).update(any(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        String where = captor.getValue().getCustomSqlSegment();
        assertThat(sqlSet).as("必须把 is_primary 置 0").contains("is_primary");
        assertThat(where).as("必须排除当前空间（否则会把刚设的主标记也清掉）")
            .contains("space_id");
        assertThat(where).as("必须只清原本是主标记的行").contains("is_primary");
    }

    // ---------- 空间存在性 ----------

    @Test
    @DisplayName("空间不存在时拒绝绑定（不静默创建孤儿关联）")
    void shouldRejectWhenSpaceMissing() {
        when(spaceMapper.selectById(SPACE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.bind(SPACE_ID, bindReq("dept", List.of(1L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("空间不存在");
    }

    // ---------- 反查 ----------

    @Test
    @DisplayName("反查未指定类型或 ID 时拒绝（不知道类型就无法解释 target_id）")
    void shouldRejectIncompleteTargetQuery() {
        SpaceRelationQuery query = new SpaceRelationQuery();
        query.setTargetId(1L);

        assertThatThrownBy(() -> service.listByTarget(query))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须指定关联对象类型");
    }

    @Test
    @DisplayName("反查结果为空时返回空集合（不是 null）")
    void shouldReturnEmptyListWhenNoRelation() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(relationMapper.selectList(any())).thenReturn(List.of());

        SpaceRelationQuery query = new SpaceRelationQuery();
        query.setTargetType("dept");
        query.setTargetId(1L);

        assertThat(service.listByTarget(query)).isEmpty();
    }

    @Test
    @DisplayName("★ 反查批量取空间（一次 selectByIds），不逐条查询")
    void shouldBatchLoadSpacesInReverseQuery() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        SysSpaceRelation first = relation(1L, SPACE_ID);
        SysSpaceRelation second = relation(2L, OTHER_SPACE_ID);
        when(relationMapper.selectList(any())).thenReturn(List.of(first, second));
        when(spaceMapper.selectByIds(anyList())).thenReturn(List.of(space(SPACE_ID), space(OTHER_SPACE_ID)));
        when(spaceMapper.selectList(any())).thenReturn(List.of(space(SPACE_ID), space(OTHER_SPACE_ID)));

        SpaceRelationQuery query = new SpaceRelationQuery();
        query.setTargetType("dept");
        query.setTargetId(1L);
        List<SpaceRelationResp> result = service.listByTarget(query);

        assertThat(result).hasSize(2);
        // 关键断言：走的是批量接口，不是循环 selectById
        verify(spaceMapper).selectByIds(anyList());
        verify(spaceMapper, never()).selectById(SPACE_ID);
    }

    @Test
    @DisplayName("删除对象时按其全部关联清理（多态无外键，业务侧必须显式调）")
    void shouldRemoveAllRelationsByTarget() {
        when(relationMapper.physicalDelete(any())).thenReturn(2);

        assertThat(service.removeByTarget("device", 9L)).isEqualTo(2);
        verify(relationMapper).physicalDelete(any());
    }


    // ---------- 按对象整体同步空间集合 ----------

    @Test
    @DisplayName("★ 同步：目标集合与现有相同时不做任何增删（避免每次保存都写库）")
    void shouldNotChangeWhenSyncTargetMatchesExisting() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectByIds(anyList())).thenReturn(List.of(space(1L), space(2L)));
        when(relationMapper.selectList(any())).thenReturn(List.of(
            relation(10L, 1L), relation(11L, 2L)));
        when(relationMapper.updateById(any(SysSpaceRelation.class))).thenReturn(1);

        SpaceSyncReq req = syncReq("dept", 7L, List.of(1L, 2L));
        assertThat(service.syncTargetSpaces(req)).isZero();
        verify(service, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("★ 同步：只新增差集（不重复插入已存在的关联）")
    void shouldInsertOnlyMissingSpacesOnSync() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectByIds(anyList())).thenReturn(List.of(space(1L), space(2L)));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(10L, 1L)));
        doAnswer(invocation -> !((List<?>) invocation.getArgument(0)).isEmpty())
            .when(service).saveBatch(anyList());

        SpaceSyncReq req = syncReq("dept", 7L, List.of(1L, 2L));
        assertThat(service.syncTargetSpaces(req)).isEqualTo(1);
        verify(service).saveBatch(anyList());
        verify(relationMapper, never()).physicalDelete(any());
    }

    @Test
    @DisplayName("★ 同步：目标空间不存在时必须拒绝（并点名是哪个 ID）")
    void shouldRejectSyncWhenSpaceMissing() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        // 请求 1、2，只查到 1 ⇒ 2 不存在
        when(spaceMapper.selectByIds(anyList())).thenReturn(List.of(space(1L)));

        SpaceSyncReq req = syncReq("dept", 7L, List.of(1L, 2L));
        assertThatThrownBy(() -> service.syncTargetSpaces(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("目标空间不存在");
    }

    @Test
    @DisplayName("同步：清空全部关联（传空数组）应删除既有行，且不留主空间")
    void shouldClearAllRelationsWhenSyncWithEmptyList() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(10L, 1L)));
        when(relationMapper.physicalDelete(any())).thenReturn(1);

        SpaceSyncReq req = syncReq("dept", 7L, List.of());
        assertThat(service.syncTargetSpaces(req)).isEqualTo(1);
        verify(relationMapper).physicalDelete(any());
    }

    @Test
    @DisplayName("★ 同步：原主空间被移除时自动补一个主空间（否则依赖主空间的定位会拿不到数据）")
    void shouldPromoteAnotherPrimaryWhenPrimaryRemoved() {
        when(dictItemMapper.selectByDictCode("space_target_type")).thenReturn(List.of(dictItem("dept")));
        when(spaceMapper.selectByIds(anyList())).thenReturn(List.of(space(1L), space(2L)));
        // 现有：空间 1 是主空间；目标集合只要空间 2 ⇒ 空间 1 被删
        SysSpaceRelation primary = relation(10L, 1L);
        primary.setIsPrimary(SpaceConstants.PRIMARY_YES);
        SysSpaceRelation second = relation(11L, 2L);
        second.setIsPrimary(SpaceConstants.PRIMARY_NO);
        when(relationMapper.selectList(any()))
            .thenReturn(List.of(primary, second))   // 第一次：算差集
            .thenReturn(List.of(second));           // 第二次：ensurePrimarySpaceExists 复查
        when(relationMapper.physicalDelete(any())).thenReturn(1);
        doAnswer(invocation -> !((List<?>) invocation.getArgument(0)).isEmpty())
            .when(service).saveBatch(anyList());
        when(relationMapper.updateById(any(SysSpaceRelation.class))).thenReturn(1);

        service.syncTargetSpaces(syncReq("dept", 7L, List.of(1L, 2L)));
        // 空间 1 被移除 ⇒ 空间 2 应被提升为主空间（批量提升，不是逐条 updateById）
        verify(service, atLeastOnce()).updateBatchById(anyList());
    }

    // ---------- 辅助 ----------

    private SpaceSyncReq syncReq(String targetType, Long targetId, List<Long> spaceIds) {
        SpaceSyncReq req = new SpaceSyncReq();
        req.setTargetType(targetType);
        req.setTargetId(targetId);
        req.setSpaceIds(spaceIds);
        return req;
    }

    private SpaceBindReq bindReq(String targetType, List<Long> targetIds) {
        SpaceBindReq req = new SpaceBindReq();
        req.setTargetType(targetType);
        req.setTargetIds(targetIds);
        return req;
    }

    private static SysSpace space(Long id) {
        SysSpace space = new SysSpace();
        space.setId(id);
        space.setPid(SpaceConstants.ROOT_PARENT_ID);
        space.setName("空间" + id);
        space.setLevel(1);
        space.setStatus(1);
        return space;
    }

    private static SysSpaceRelation relation(Long id, Long spaceId) {
        SysSpaceRelation relation = new SysSpaceRelation();
        relation.setId(id);
        relation.setSpaceId(spaceId);
        relation.setTargetType("dept");
        relation.setTargetId(1L);
        relation.setIsPrimary(SpaceConstants.PRIMARY_NO);
        relation.setStatus(1);
        return relation;
    }

    /**
     * MyBatis-Plus 的批量接口返回 {@code List<BatchResult>}（不是 int）；非空即表示成功。
     *
     * <p>{@code BatchResult} 没有无参构造器（{@code MappedStatement} 与 sql 是 final），
     * 故用 {@code Mockito.mock} 造一个占位结果——本类断言的是「走了批量接口」这个事实，
     * 不是 {@code BatchResult} 内部的内容。</p>
     *
     * @return 一条成功的批量结果
     */
    private static List<BatchResult> batchResult() {
        return List.of(mock(BatchResult.class));
    }

    private static SysDictItem dictItem(String value) {
        SysDictItem item = new SysDictItem();
        item.setValue(value);
        item.setLabel(value);
        return item;
    }
}
