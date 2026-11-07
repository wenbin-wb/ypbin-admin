/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysAccessBlocklist;
import cn.ypbin.admin.system.mapper.SysAccessBlocklistMapper;
import cn.ypbin.admin.system.model.req.AccessBlocklistBanReq;
import cn.ypbin.admin.system.model.req.AccessBlocklistSaveReq;
import cn.ypbin.admin.system.service.event.BlocklistRulesChangedEvent;
import cn.ypbin.starter.core.blocklist.BlocklistMatchType;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 访问黑名单服务用例。
 *
 * @author wenbin
 * @since 2026-11-05
 */
@ExtendWith(MockitoExtension.class)
class SysAccessBlocklistServiceImplTest {

    @Mock
    private SysAccessBlocklistMapper mapper;

    @Mock
    private BlocklistStore blocklistStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SysAccessBlocklistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysAccessBlocklistServiceImpl(blocklistStore, eventPublisher);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    @DisplayName("非法 IP/CIDR 在入库前拒绝")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> service.createBlocklist(req("IP", "999.1.1.1")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("IP");
        assertThatThrownBy(() -> service.createBlocklist(req("CIDR", "10.0.0.0/33")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("CIDR");
    }

    @Test
    @DisplayName("PATH 自动补前导斜杠，UA 少于 3 字符拒绝")
    void normalizesPathAndRejectsShortUserAgent() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SysAccessBlocklist.class))).thenReturn(1);

        service.createBlocklist(req("PATH", "admin"));

        ArgumentCaptor<SysAccessBlocklist> captor = ArgumentCaptor.forClass(SysAccessBlocklist.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getMatchValue()).isEqualTo("/admin");
        assertThat(captor.getValue().getMatchType()).isEqualTo("PATH");

        assertThatThrownBy(() -> service.createBlocklist(req("USER_AGENT", "ab")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("User-Agent");
    }

    @Test
    @DisplayName("状态只能为 0/1，到期时间必须晚于当前")
    void validatesStatusAndExpireTime() {
        AccessBlocklistSaveReq status = req("IP", "10.0.0.1");
        status.setStatus(2);
        assertThatThrownBy(() -> service.createBlocklist(status))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("状态");

        AccessBlocklistSaveReq expired = req("IP", "10.0.0.1");
        expired.setExpireTime(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.createBlocklist(expired))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("到期时间");
    }

    @Test
    @DisplayName("重复规则拒绝且不写库")
    void rejectsDuplicate() {
        when(mapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createBlocklist(req("IP", "10.0.0.1")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已存在");
        verify(mapper, never()).insert(any(SysAccessBlocklist.class));
    }

    @Test
    @DisplayName("规则变更发布提交后事件而不是直接下发")
    void publishesRulesChangedEvent() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SysAccessBlocklist.class))).thenReturn(1);

        service.createBlocklist(req("IP", "10.0.0.1"));

        verify(eventPublisher).publishEvent(any(Object.class));
        verify(blocklistStore, never()).replaceRules(any());
    }

    @Test
    @DisplayName("下发映射保留主键字符串、启用状态与到期时间")
    void publishesRuleMapping() {
        SysAccessBlocklist entity = new SysAccessBlocklist();
        entity.setId(42L);
        entity.setMatchType("IP");
        entity.setMatchValue("10.0.0.1");
        entity.setReason("bad");
        LocalDateTime expire = LocalDateTime.now().plusHours(1);
        entity.setExpireTime(expire);
        when(mapper.selectList(any())).thenReturn(List.of(entity));

        service.refreshRules();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<BlocklistRule>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(blocklistStore).replaceRules(captor.capture());
        BlocklistRule rule = captor.getValue().iterator().next();
        assertThat(rule.getId()).isEqualTo("42");
        assertThat(rule.getType()).isEqualTo(BlocklistMatchType.IP);
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getExpireAt()).isEqualTo(expire.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("自动封禁列表读取失败时降级为空列表")
    void autoBansFallsBackToEmpty() {
        when(blocklistStore.autoBans()).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.listAutoBans()).isEmpty();
    }

    @Test
    @DisplayName("临时封禁归一化 IP 并使用 TTL")
    void bansNormalizedIp() {
        AccessBlocklistBanReq req = new AccessBlocklistBanReq();
        req.setClientIp("::ffff:10.0.0.1");
        req.setBanSeconds(600);
        req.setReason("manual");

        service.ban(req);

        verify(blocklistStore).ban("10.0.0.1", Duration.ofSeconds(600), "manual");
    }

    @Test
    @DisplayName("非法 IP 的封禁/解封直接拒绝")
    void rejectsInvalidBanAndUnban() {
        AccessBlocklistBanReq req = new AccessBlocklistBanReq();
        req.setClientIp("not-an-ip");
        req.setBanSeconds(600);

        assertThatThrownBy(() -> service.ban(req)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.unban("example.com")).isInstanceOf(BusinessException.class);
        verify(blocklistStore, never()).ban(any(), any(), any());
        verify(blocklistStore, never()).unban(any());
    }

    @Test
    @DisplayName("下发监听器固定在事务提交后执行，且失败不反抛")
    void publishesAfterCommitAndSwallowsFailure() throws Exception {
        var method = SysAccessBlocklistServiceImpl.class
            .getMethod("onRulesChanged", BlocklistRulesChangedEvent.class);
        var annotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);

        when(mapper.selectList(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("redis down"))
            .when(blocklistStore).replaceRules(any());

        service.onRulesChanged(new BlocklistRulesChangedEvent());

        verify(blocklistStore).replaceRules(any());
    }

    private static AccessBlocklistSaveReq req(String type, String value) {
        AccessBlocklistSaveReq req = new AccessBlocklistSaveReq();
        req.setMatchType(type);
        req.setMatchValue(value);
        req.setStatus(1);
        return req;
    }
}
