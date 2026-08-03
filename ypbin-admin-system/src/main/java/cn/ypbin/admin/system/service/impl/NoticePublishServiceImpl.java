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

import cn.ypbin.admin.system.entity.SysMessage;
import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.admin.system.service.SysDeptService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.messaging.mail.MailService;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sms.SmsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 公告发布推送实现。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Service
@RequiredArgsConstructor
public class NoticePublishServiceImpl implements NoticePublishService {

    private static final Logger log = LoggerFactory.getLogger(NoticePublishServiceImpl.class);

    /** 通知范围 */
    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_ROLE = 2;
    private static final int SCOPE_DEPT = 3;
    private static final int SCOPE_USER = 4;

    private final SysUserService userService;
    private final SysDeptService deptService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMessageMapper messageMapper;
    private final MailService mailService;
    private final PushService pushService;
    // 短信服务为可选：未引入/未配置时不阻断发布
    private final ObjectProvider<SmsService> smsServiceProvider;

    @Override
    public void dispatch(SysNotice notice) {
        List<SysUser> targets = resolveTargets(notice);
        if (targets.isEmpty()) {
            log.info("[公告推送] 公告[{}] 无目标用户，跳过", notice.getId());
            return;
        }
        Set<String> methods = parseMethods(notice.getNotifyMethods());
        // 站内信默认必发（即便未勾选，也在系统内留存一条）
        writeMessages(notice, targets);
        pushUnread(targets);
        if (methods.contains("email")) {
            sendEmails(notice, targets);
        }
        if (methods.contains("sms")) {
            sendSms(notice, targets);
        }
    }

    /**
     * 按通知范围解析目标用户。全体=全部启用用户；角色/部门/用户按 scopeTargetIds 过滤。
     */
    private List<SysUser> resolveTargets(SysNotice notice) {
        Integer scope = notice.getNoticeScope() == null ? SCOPE_ALL : notice.getNoticeScope();
        if (scope == SCOPE_ALL) {
            return userService.list(enabledUsers());
        }
        List<Long> ids = parseIds(notice.getScopeTargetIds());
        if (ids.isEmpty()) {
            return List.of();
        }
        return switch (scope) {
            case SCOPE_ROLE -> {
                List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, ids))
                    .stream().map(SysUserRole::getUserId).distinct().toList();
                yield userIds.isEmpty() ? List.<SysUser>of()
                    : userService.list(enabledUsers().in(SysUser::getId, userIds));
            }
            case SCOPE_DEPT -> userService.list(enabledUsers().in(SysUser::getDeptId, ids));
            case SCOPE_USER -> userService.list(enabledUsers().in(SysUser::getId, ids));
            default -> List.of();
        };
    }

    private LambdaQueryWrapper<SysUser> enabledUsers() {
        return new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1);
    }

    private List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                try {
                    ids.add(Long.parseLong(t));
                } catch (NumberFormatException ignore) {
                    // 跳过非法 ID
                }
            }
        }
        return ids;
    }

    private Set<String> parseMethods(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * 为每个目标用户写一条站内信。
     */
    private void writeMessages(SysNotice notice, List<SysUser> targets) {
        for (SysUser user : targets) {
            SysMessage msg = new SysMessage();
            msg.setReceiverUserId(user.getId());
            msg.setTitle(notice.getTitle());
            msg.setContent(notice.getContent());
            // 1 系统通知
            msg.setMessageType(1);
            msg.setReadStatus(0);
            messageMapper.insert(msg);
        }
    }

    /**
     * 给在线目标用户推送未读数变更事件（前端据此刷新铃铛）。
     */
    private void pushUnread(List<SysUser> targets) {
        for (SysUser user : targets) {
            try {
                pushService.sendToUser(String.valueOf(user.getId()), "message-unread", 1);
            } catch (Exception e) {
                log.debug("[公告推送] SSE 推送失败 user={}: {}", user.getId(), e.getMessage());
            }
        }
    }

    /**
     * 发送邮件。未配置 SMTP 时 MailService 会抛异常，此处捕获并记日志、不阻断。
     */
    private void sendEmails(SysNotice notice, List<SysUser> targets) {
        for (SysUser user : targets) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            try {
                mailService.sendHtml(user.getEmail(), notice.getTitle(), notice.getContent());
            } catch (Exception e) {
                log.warn("[公告推送] 邮件发送失败 to={}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * 发送短信。短信服务未引入/未配置时静默跳过。
     */
    private void sendSms(SysNotice notice, List<SysUser> targets) {
        SmsService smsService = smsServiceProvider.getIfAvailable();
        if (smsService == null || !smsService.isConfigured()) {
            log.info("[公告推送] 短信服务未配置，跳过短信通知 notice={}", notice.getId());
            return;
        }
        for (SysUser user : targets) {
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                continue;
            }
            try {
                smsService.send(user.getPhone(), notice.getTitle());
            } catch (Exception e) {
                log.warn("[公告推送] 短信发送失败 to={}: {}", user.getPhone(), e.getMessage());
            }
        }
    }
}

