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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.constant.SpaceConstants;
import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.admin.system.mapper.SysSpaceMapper;
import cn.ypbin.admin.system.mapper.SysSpaceRelationMapper;
import cn.ypbin.admin.system.model.req.SpaceSaveReq;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 空间树的<b>父链不变量</b>测试：父存在、防成环、深度上限、层级计算。
 *
 * <p><b>为什么这些用例必须存在</b>：数据库拦不住「把空间挂到自己的后代下」，也拦不住无界深度。
 * 这两条只靠 {@code validateParent} 守——写错一行不会报错、不会红任何既有用例，
 * 只会让空间树页面在某个租户的数据上无限递归。而 {@code TreeUtils.build} 遇环时不抛错，
 * 它把成环节点<b>直接从结果里丢掉</b>（既不是根也挂不到根），表现为「空间莫名消失」。
 * 故这里逐条钉住：成环拒绝、断链拒绝、超深拒绝、深度正确。</p>
 *
 * <p><b>测试手法</b>：直接驱动 {@code selectParentChain} 的返回（父链查询是唯一的外部输入），
 * 断言的是「给定父链形状 → 业务判定结果」。这比起完整 Spring 上下文更聚焦：
 * 本类要验证的是判定逻辑，不是 MyBatis 能否执行递归 CTE（那由集成测试覆盖）。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
class SysSpaceServiceImplTest {

    /** 父链 CTE 的深度上限（业务上限 + 余量），与实现同源。 */
    private static final int CHAIN_MAX_DEPTH =
        SpaceConstants.MAX_SPACE_DEPTH + SpaceConstants.PARENT_CHAIN_MARGIN;

    private static final Long SELF_ID = 100L;

    /** 测试租户（递归 CTE 显式传租户条件，见 SysSpaceMapper 类注释）。 */
    private static final Long TENANT_ID = 1L;

    private SysSpaceMapper spaceMapper;

    private SysSpaceRelationMapper relationMapper;

    private SysDictItemMapper dictItemMapper;

    private SysSpaceServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "space"),
            SysSpace.class);
    }

    @BeforeEach
    void setUp() {
        spaceMapper = mock(SysSpaceMapper.class);
        relationMapper = mock(SysSpaceRelationMapper.class);
        dictItemMapper = mock(SysDictItemMapper.class);
        service = new SysSpaceServiceImpl(relationMapper, dictItemMapper, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", spaceMapper);
        // 空间类型校验依赖字典（space_type），默认给一组内置类型
        when(dictItemMapper.selectByDictCode("space_type")).thenReturn(List.of(
            dictItem("park"), dictItem("building"), dictItem("floor"), dictItem("office")));
    }

    // ---------- 父存在 ----------

    @Test
    @DisplayName("父空间不存在时拒绝新增（不能静默挂到 0 变成顶级）")
    void shouldRejectWhenParentMissing() {
        when(spaceMapper.selectParentChain(eq(999L), eq(TENANT_ID), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req(999L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("父空间不存在");
        verify(spaceMapper, never()).insert(any(SysSpace.class));
    }

    @Test
    @DisplayName("父空间为 0 时直接放行为顶级，不查父链")
    void shouldAllowRootWithoutChainQuery() {
        when(spaceMapper.selectParentChain(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        when(spaceMapper.insert(any(SysSpace.class))).thenReturn(1);

        withTenant(() -> service.createSpace(req(SpaceConstants.ROOT_PARENT_ID)));
        // 顶级不查父链：省掉一次必然为空的递归 CTE（这才是本用例的实质断言；
        // 原先那句 assertThat(...).isNull() 是 mock 未设 id 的必然结果，属假保护）
        verify(spaceMapper, never()).selectParentChain(anyLong(), anyLong(), anyInt());
    }

    // ---------- 防成环 ----------

    @Test
    @DisplayName("★ 成环拒绝：父链上出现当前空间自身")
    void shouldRejectCycleWhenSelfAppearsInParentChain() {
        // 把 SELF_ID 挂到 200 下，而 200 的父链是 200 → 100(SELF) → ... ⇒ 成环
        when(spaceMapper.selectById(SELF_ID)).thenReturn(space(SELF_ID, 0L));
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt())).thenReturn(List.of(
            row(200L, SELF_ID), row(SELF_ID, 300L), row(300L, 0L)));

        assertThatThrownBy(() -> withTenantVoid(() -> service.updateSpace(SELF_ID, req(200L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能挂到自己的后代下");
    }

    @Test
    @DisplayName("成环时 update 不得落库（否则树页面会静默丢节点）")
    void shouldNotPersistWhenCycleDetected() {
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt())).thenReturn(List.of(
            row(200L, SELF_ID), row(SELF_ID, 0L)));
        when(spaceMapper.selectById(SELF_ID)).thenReturn(space(SELF_ID, 0L));

        assertThatThrownBy(() -> withTenantVoid(() -> service.updateSpace(SELF_ID, req(200L))))
            .isInstanceOf(BusinessException.class);
        verify(spaceMapper, never()).updateById(any(SysSpace.class));
    }

    @Test
    @DisplayName("自引用（父 = 自己）拒绝")
    void shouldRejectSelfAsParent() {
        when(spaceMapper.selectById(SELF_ID)).thenReturn(space(SELF_ID, 0L));

        assertThatThrownBy(() -> withTenantVoid(() -> service.updateSpace(SELF_ID, req(SELF_ID))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能作为自己的父空间");
    }

    @Test
    @DisplayName("断链拒绝：父链到不了根（父已被删/数据脏）")
    void shouldRejectBrokenChain() {
        // 链尾 pid=77 但 77 不在链里 ⇒ 断链，不是成环也不是正常树
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt())).thenReturn(List.of(
            row(200L, 77L)));

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req(200L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("父子关系成环或层级过深");
    }

    // ---------- 深度上限 ----------

    @Test
    @DisplayName("★ 超深拒绝：父链长度 + 自身 > MAX_SPACE_DEPTH")
    void shouldRejectWhenExceedingMaxDepth() {
        // 构造恰好到上限的父链：父链长度 = MAX_SPACE_DEPTH，自己再加一层即超限
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(SpaceConstants.MAX_SPACE_DEPTH));

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req(200L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("空间层级不能超过");
    }

    @Test
    @DisplayName("恰好等于上限时允许（边界值不能误杀）")
    void shouldAllowExactlyAtMaxDepth() {
        // 父链长度 = MAX_SPACE_DEPTH - 1 ⇒ 自身为第 MAX_SPACE_DEPTH 层，正好等于上限
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(SpaceConstants.MAX_SPACE_DEPTH - 1));
        when(spaceMapper.insert(any(SysSpace.class))).thenReturn(1);

        withTenant(() -> service.createSpace(req(200L)));

        verify(spaceMapper).insert(any(SysSpace.class));
    }

    @Test
    @DisplayName("层级计算：顶级 = 1，父链长度 N 的子 = N + 1")
    void shouldComputeLevelFromChainLength() {
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(2));
        when(spaceMapper.insert(any(SysSpace.class))).thenReturn(1);

        withTenant(() -> service.createSpace(req(200L)));

        // 父链长度 2（父、祖父）⇒ 自身是第 3 层
        ArgumentCaptor<SysSpace> captor = ArgumentCaptor.forClass(SysSpace.class);
        verify(spaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(3);
    }

    // ---------- 绝对根空间的层级 ----------

    @Test
    @DisplayName("顶级空间的 level 恒为 1（不取信入参）")
    void shouldForceLevelOneForRoot() {
        when(spaceMapper.insert(any(SysSpace.class))).thenReturn(1);

        withTenant(() -> service.createSpace(req(SpaceConstants.ROOT_PARENT_ID)));

        ArgumentCaptor<SysSpace> captor = ArgumentCaptor.forClass(SysSpace.class);
        verify(spaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(1);
    }


    @Test
    @DisplayName("★ 同层换父也必须刷新子树（只看 level 相等会漏：历史脏数据的 level 可能已错）")
    void shouldRefreshSubtreeEvenWhenLevelUnchanged() {
        // SELF 原在 A(第2层) 下，改挂到 B(第2层) 下：newLevel == oldLevel == 2，但父变了
        SysSpace self = space(SELF_ID, 300L);
        self.setLevel(2);
        when(spaceMapper.selectById(SELF_ID)).thenReturn(self);
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(1));
        when(spaceMapper.updateById(any(SysSpace.class))).thenReturn(1);

        withTenantVoid(() -> service.updateSpace(SELF_ID, req(200L)));

        // 父变了 ⇒ 必须刷新子树，否则子树的绝对深度基准与库里的 level 不一致
        verify(spaceMapper).refreshSubtreeLevel(eq(SELF_ID), eq(TENANT_ID), eq(2), anyInt());
    }

    @Test
    @DisplayName("★ 同一操作只查一次父链（校验与层级计算合并，消除竞态窗口与重复往返）")
    void shouldQueryParentChainOnlyOncePerWrite() {
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(2));
        when(spaceMapper.insert(any(SysSpace.class))).thenReturn(1);

        withTenant(() -> service.createSpace(req(200L)));

        // 之前是 validateParent 一次 + computeLevel 一次 = 2 次同一条 CTE
        verify(spaceMapper, times(1)).selectParentChain(eq(200L), eq(TENANT_ID), anyInt());
    }

    @Test
    @DisplayName("父与层级都没变时不做子树刷新（避免每次保存都触发一次全子树 UPDATE）")
    void shouldNotRefreshSubtreeWhenNothingChanged() {
        SysSpace self = space(SELF_ID, 300L);
        self.setLevel(2);
        when(spaceMapper.selectById(SELF_ID)).thenReturn(self);
        // 父链长度 1 ⇒ 新层级 2，与旧 level 相同；父也没变
        when(spaceMapper.selectParentChain(eq(300L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(1));
        when(spaceMapper.updateById(any(SysSpace.class))).thenReturn(1);

        withTenantVoid(() -> service.updateSpace(SELF_ID, req(300L)));

        verify(spaceMapper, never()).refreshSubtreeLevel(anyLong(), anyLong(), anyInt(), anyInt());
    }


    @Test
    @DisplayName("★ 换父导致子树超深必须拒绝（只校验自身深度会漏：3 层子树挪到第 4 层 ⇒ 最深到第 7 层）")
    void shouldRejectWhenSubtreeWouldExceedMaxDepth() {
        SysSpace self = space(SELF_ID, 300L);
        self.setLevel(2);
        when(spaceMapper.selectById(SELF_ID)).thenReturn(self);
        // 目标父在第 4 层（父链长度 4）
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(4));
        // 该空间带 2 代后代（相对深度 2）⇒ 移动后最深 = 5 + 2 = 7 > 6
        when(spaceMapper.selectSubtreeMaxRelDepth(eq(SELF_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(2);

        assertThatThrownBy(() -> withTenantVoid(() -> service.updateSpace(SELF_ID, req(200L))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("超过上限");
        verify(spaceMapper, never()).updateById(any(SysSpace.class));
    }

    @Test
    @DisplayName("子树不超深时放行（边界：最深恰好等于上限）")
    void shouldAllowWhenSubtreeReachesExactlyMaxDepth() {
        SysSpace self = space(SELF_ID, 300L);
        self.setLevel(2);
        when(spaceMapper.selectById(SELF_ID)).thenReturn(self);
        // 父在第 3 层 ⇒ 自身第 4 层；子树相对深度 2 ⇒ 最深第 6 层 = 上限，恰好放行
        when(spaceMapper.selectParentChain(eq(200L), eq(TENANT_ID), anyInt()))
            .thenReturn(chainOfLength(3));
        when(spaceMapper.selectSubtreeMaxRelDepth(eq(SELF_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(2);
        when(spaceMapper.updateById(any(SysSpace.class))).thenReturn(1);

        withTenantVoid(() -> service.updateSpace(SELF_ID, req(200L)));

        verify(spaceMapper).updateById(any(SysSpace.class));
    }


    @Test
    @DisplayName("★ 空间类型必须是字典登记值（直接调 API 写脏类型会让页面翻译不出、筛选永远匹配不上）")
    void shouldRejectUnknownSpaceType() {
        SpaceSaveReq req = req(SpaceConstants.ROOT_PARENT_ID);
        req.setSpaceType("not_a_real_type");

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("空间类型不合法");
        verify(spaceMapper, never()).insert(any(SysSpace.class));
    }

    @Test
    @DisplayName("扩展属性非法 JSON 必须拒绝（写坏 JSON 会让读取方每次解析失败且无从定位）")
    void shouldRejectMalformedExtJson() {
        SpaceSaveReq req = req(SpaceConstants.ROOT_PARENT_ID);
        req.setExtJson("{not valid json");

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("合法的 JSON");
    }

    @Test
    @DisplayName("扩展属性超 16KB 必须拒绝（防把整份配置塞进扩展字段）")
    void shouldRejectOversizedExtJson() {
        SpaceSaveReq req = req(SpaceConstants.ROOT_PARENT_ID);
        // 构造超过 16KB 的合法 JSON
        req.setExtJson("{\"k\":\"" + "x".repeat(SpaceConstants.MAX_EXT_JSON_BYTES) + "\"}");

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("扩展属性过大");
    }

    @Test
    @DisplayName("空间编码重复必须拒绝（租户内唯一）")
    void shouldRejectDuplicateCode() {
        SpaceSaveReq req = req(SpaceConstants.ROOT_PARENT_ID);
        req.setCode("A101");
        when(spaceMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> withTenant(() -> service.createSpace(req)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("空间编码已存在");
    }

    @Test
    @DisplayName("★ 删除有子空间的节点必须拒绝（不做级联删除，避免误删整棵空间树）")
    void shouldRejectDeleteWhenChildrenExist() {
        when(spaceMapper.selectById(SELF_ID)).thenReturn(space(SELF_ID, 0L));
        when(spaceMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> withTenantVoid(() -> service.deleteSpace(SELF_ID)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("子空间");
        verify(spaceMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("★ 删除仍挂着业务对象的空间必须拒绝（幽灵关联会让空间永远删不掉，所以必须报清楚）")
    void shouldRejectDeleteWhenRelationsExist() {
        when(spaceMapper.selectById(SELF_ID)).thenReturn(space(SELF_ID, 0L));
        when(spaceMapper.selectCount(any())).thenReturn(0L);
        when(relationMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> withTenantVoid(() -> service.deleteSpace(SELF_ID)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仍挂着");
    }

    // ---------- 辅助 ----------

    private SpaceSaveReq req(Long pid) {
        SpaceSaveReq req = new SpaceSaveReq();
        req.setPid(pid);
        req.setName("测试空间");
        req.setSpaceType("office");
        req.setStatus(1);
        return req;
    }

    /**
     * 在「带租户的身份头」下执行。
     *
     * <p>服务端取租户走 {@code UserContext.getTenantId()}（读身份头），
     * 等价于生产里 {@code IdentityHeaderFilter} 写入 {@code IdentityContext} 的效果。</p>
     *
     * @param action 被测动作
     * @param <T>    返回类型
     * @return 动作结果
     */
    private static <T> T withTenant(java.util.function.Supplier<T> action) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(1L);
        loginUser.setTenantId(TENANT_ID);
        IdentityContext.setLoginUser(loginUser);
        try {
            return action.get();
        } finally {
            IdentityContext.clear();
        }
    }

    private static void withTenantVoid(Runnable action) {
        withTenant(() -> {
            action.run();
            return null;
        });
    }

    private static SysDictItem dictItem(String value) {
        SysDictItem item = new SysDictItem();
        item.setValue(value);
        item.setLabel(value);
        return item;
    }

    private static SysSpace space(Long id, Long pid) {
        SysSpace space = new SysSpace();
        space.setId(id);
        space.setPid(pid);
        space.setName("节点" + id);
        space.setLevel(1);
        space.setStatus(1);
        return space;
    }

    private static Map<String, Object> row(Long id, Long pid) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("pid", pid);
        row.put("name", "节点" + id);
        return row;
    }

    /**
     * 构造一条长度为 {@code length} 的合法父链（自身是链首、链尾 pid=0）。
     *
     * @param length 链长度
     * @return 父链行（depth 升序）
     */
    private static List<Map<String, Object>> chainOfLength(int length) {
        if (length <= 0) {
            return List.of();
        }
        List<Map<String, Object>> chain = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            long id = 200L + i;
            long pid = (i == length - 1) ? SpaceConstants.ROOT_PARENT_ID : 200L + i + 1;
            chain.add(row(id, pid));
        }
        return chain;
    }
}
