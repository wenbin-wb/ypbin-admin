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

import cn.ypbin.admin.system.entity.SysAccessBlocklist;
import cn.ypbin.admin.system.mapper.SysAccessBlocklistMapper;
import cn.ypbin.admin.system.model.query.AccessBlocklistQuery;
import cn.ypbin.admin.system.model.req.AccessBlocklistBanReq;
import cn.ypbin.admin.system.model.req.AccessBlocklistSaveReq;
import cn.ypbin.admin.system.model.resp.AccessBlocklistResp;
import cn.ypbin.admin.system.model.resp.AutoBanResp;
import cn.ypbin.admin.system.service.SysAccessBlocklistService;
import cn.ypbin.admin.system.service.event.BlocklistRulesChangedEvent;
import cn.ypbin.starter.core.blocklist.AutoBanEntry;
import cn.ypbin.starter.core.blocklist.BlocklistMatchType;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.IpAddress;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 访问黑名单服务实现。
 *
 * <p>数据库是规则事实源，每次写操作后把启用规则全量下发到 {@link BlocklistStore}（Redis 或内存）。
 * 应用启动完成时同步一次，保证 Redis 被清空或规则表被直接修改后仍能恢复。</p>
 *
 * @author wenbin
 * @since 2026-11-05
 */
@Service
@RequiredArgsConstructor
public class SysAccessBlocklistServiceImpl extends BaseServiceImpl<SysAccessBlocklistMapper, SysAccessBlocklist>
    implements SysAccessBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(SysAccessBlocklistServiceImpl.class);

    private final BlocklistStore blocklistStore;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<AccessBlocklistResp> pageBlocklists(AccessBlocklistQuery query) {
        PageResult<SysAccessBlocklist> source = page(query, new LambdaQueryWrapper<SysAccessBlocklist>()
            .eq(StringUtils.hasText(query.getMatchType()), SysAccessBlocklist::getMatchType,
                normalizeType(query.getMatchType()))
            .like(StringUtils.hasText(query.getMatchValue()), SysAccessBlocklist::getMatchValue,
                query.getMatchValue())
            .eq(query.getStatus() != null, SysAccessBlocklist::getStatus, query.getStatus())
            .orderByDesc(SysAccessBlocklist::getCreateTime));
        List<AccessBlocklistResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBlocklist(AccessBlocklistSaveReq req) {
        Normalized normalized = normalize(req);
        checkDuplicate(normalized.type(), normalized.value(), null);
        SysAccessBlocklist entity = new SysAccessBlocklist();
        entity.setMatchType(normalized.type().name());
        entity.setMatchValue(normalized.value());
        entity.setReason(req.getReason());
        entity.setExpireTime(req.getExpireTime());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        if (!save(entity)) {
            throw new BusinessException("黑名单规则新增失败");
        }
        markRulesChanged();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBlocklist(Long id, AccessBlocklistSaveReq req) {
        SysAccessBlocklist existing = getById(id);
        if (existing == null) {
            throw new BusinessException("黑名单规则不存在");
        }
        Normalized normalized = normalize(req);
        checkDuplicate(normalized.type(), normalized.value(), id);
        SysAccessBlocklist entity = new SysAccessBlocklist();
        entity.setId(id);
        entity.setMatchType(normalized.type().name());
        entity.setMatchValue(normalized.value());
        entity.setStatus(req.getStatus());
        // reason/expireTime 允许被清空：用 UpdateWrapper 显式 set，避免 updateById 忽略 null
        if (!update(entity, new LambdaUpdateWrapper<SysAccessBlocklist>()
            .eq(SysAccessBlocklist::getId, id)
            .set(SysAccessBlocklist::getReason, req.getReason())
            .set(SysAccessBlocklist::getExpireTime, req.getExpireTime()))) {
            throw new BusinessException("黑名单规则修改失败");
        }
        markRulesChanged();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBlocklist(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("黑名单规则不存在");
        }
        if (!removeById(id)) {
            throw new BusinessException("黑名单规则删除失败");
        }
        markRulesChanged();
    }

    @Override
    public List<AutoBanResp> listAutoBans() {
        try {
            return blocklistStore.autoBans().stream().map(this::toResp).toList();
        } catch (RuntimeException ex) {
            // 列表是只读展示，Redis 抖动时降级为空列表，避免管理页整页 500；写操作仍上抛
            log.warn("[ypbin-admin] 读取自动封禁列表失败，返回空列表", ex);
            return List.of();
        }
    }

    @Override
    public void unban(String clientIp) {
        if (!IpAddress.isValidIp(clientIp)) {
            throw new BusinessException("IP 格式不正确");
        }
        blocklistStore.unban(clientIp);
    }

    @Override
    public void ban(AccessBlocklistBanReq req) {
        String clientIp = req.getClientIp().trim();
        if (!IpAddress.isValidIp(clientIp)) {
            throw new BusinessException("IP 格式不正确");
        }
        blocklistStore.ban(IpAddress.normalize(clientIp), Duration.ofSeconds(req.getBanSeconds()),
            StringUtils.hasText(req.getReason()) ? req.getReason() : "管理端手动封禁");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshRules() {
        publishRules();
    }

    /**
     * 规则写事务提交后再下发，避免「Redis 已生效、数据库回滚」的窗口。
     *
     * @param event 规则变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRulesChanged(BlocklistRulesChangedEvent event) {
        publishSafely("规则已提交但下发失败，等待手动重新下发或下次保存自愈");
    }

    /**
     * 应用就绪后同步一次规则；Redis 暂不可用不应阻止应用启动（过滤器会沿用空规则并记录告警）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncRulesOnStartup() {
        publishSafely("启动同步访问黑名单规则失败，等待下次保存或手动刷新");
    }

    private void markRulesChanged() {
        eventPublisher.publishEvent(new BlocklistRulesChangedEvent());
    }

    private void publishSafely(String message) {
        try {
            publishRules();
        } catch (RuntimeException ex) {
            log.warn("[ypbin-admin] " + message, ex);
        }
    }

    private void publishRules() {
        LocalDateTime now = LocalDateTime.now();
        List<SysAccessBlocklist> list = list(new LambdaQueryWrapper<SysAccessBlocklist>()
            .eq(SysAccessBlocklist::getStatus, 1)
            .and(wrapper -> wrapper.isNull(SysAccessBlocklist::getExpireTime)
                .or()
                .gt(SysAccessBlocklist::getExpireTime, now)));
        List<BlocklistRule> rules = list.stream().map(this::toRule).toList();
        blocklistStore.replaceRules(rules);
        log.debug("[ypbin-admin] 访问黑名单规则已下发：{} 条", rules.size());
    }

    private BlocklistRule toRule(SysAccessBlocklist entity) {
        BlocklistMatchType type = parseType(entity.getMatchType());
        return new BlocklistRule(String.valueOf(entity.getId()), type, entity.getMatchValue(),
            entity.getExpireTime() == null ? null
                : entity.getExpireTime().atZone(ZoneId.systemDefault()).toInstant(),
            true, entity.getReason());
    }

    private AccessBlocklistResp toResp(SysAccessBlocklist entity) {
        AccessBlocklistResp resp = new AccessBlocklistResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    private AutoBanResp toResp(AutoBanEntry entry) {
        AutoBanResp resp = new AutoBanResp();
        resp.setClientIp(entry.clientIp());
        resp.setReason(entry.reason());
        resp.setExpireTime(LocalDateTime.ofInstant(entry.expireAt(), ZoneId.systemDefault()));
        return resp;
    }

    private void checkDuplicate(BlocklistMatchType type, String value, Long excludeId) {
        boolean exists = count(new LambdaQueryWrapper<SysAccessBlocklist>()
            .eq(SysAccessBlocklist::getMatchType, type.name())
            .eq(SysAccessBlocklist::getMatchValue, value)
            .ne(excludeId != null, SysAccessBlocklist::getId, excludeId)) > 0;
        if (exists) {
            throw new BusinessException("相同匹配类型与值的规则已存在");
        }
    }

    private Normalized normalize(AccessBlocklistSaveReq req) {
        if (req.getStatus() != null && req.getStatus() != 0 && req.getStatus() != 1) {
            throw new BusinessException("状态只能为 0 或 1");
        }
        if (req.getExpireTime() != null && !req.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("到期时间必须晚于当前时间");
        }
        BlocklistMatchType type = parseType(req.getMatchType());
        String value = req.getMatchValue().trim();
        switch (type) {
            case IP -> {
                if (!IpAddress.isValidIp(value)) {
                    throw new BusinessException("IP 格式不正确");
                }
                value = IpAddress.normalize(value);
            }
            case CIDR -> {
                if (!IpAddress.isValidCidr(value)) {
                    throw new BusinessException("CIDR 格式不正确");
                }
            }
            case USER_AGENT -> {
                if (value.length() < 3) {
                    throw new BusinessException("User-Agent 关键字至少 3 个字符");
                }
            }
            case PATH -> {
                if (!value.startsWith("/")) {
                    value = "/" + value;
                }
            }
        }
        return new Normalized(type, value);
    }

    private static BlocklistMatchType parseType(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("匹配类型不能为空");
        }
        try {
            return BlocklistMatchType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("匹配类型仅支持 IP/CIDR/USER_AGENT/PATH");
        }
    }

    private static String normalizeType(String raw) {
        return StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : "";
    }

    private record Normalized(BlocklistMatchType type, String value) {
    }
}
