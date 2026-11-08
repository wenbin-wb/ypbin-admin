/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.modules.system.entity.SysNotice;
import cn.ypbin.admin.modules.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.modules.system.model.query.NoticeQuery;
import cn.ypbin.admin.modules.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.modules.system.model.resp.NoticeResp;
import cn.ypbin.admin.modules.system.service.NoticePublishService;
import cn.ypbin.admin.modules.system.service.SysNoticeService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.sensitivewords.annotation.SensitiveWordFilter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 公告服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends BaseServiceImpl<SysNoticeMapper, SysNotice>
    implements SysNoticeService {

    private static final int DRAFT = 0;
    private static final int PENDING = 1;
    private static final int PUBLISHED = 2;
    private static final int REVOKED = 3;
    private static final int IMMEDIATE = 1;
    private static final int SCHEDULED = 2;

    private final NoticePublishService noticePublishService;

    @Override
    public PageResult<NoticeResp> pageNotices(NoticeQuery query) {
        PageResult<SysNotice> source = page(query, new LambdaQueryWrapper<SysNotice>()
            // 列表不取正文（MEDIUMTEXT），正文由详情接口单独读取
            .select(SysNotice.class, info -> !"content".equals(info.getColumn()))
            .like(StringUtils.hasText(query.getTitle()), SysNotice::getTitle, query.getTitle())
            .eq(query.getNoticeType() != null, SysNotice::getNoticeType, query.getNoticeType())
            .eq(query.getPublishStatus() != null,
                SysNotice::getPublishStatus, query.getPublishStatus())
            .orderByDesc(SysNotice::getCreateTime));
        List<NoticeResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public NoticeResp getNotice(Long id) {
        // 详情必须带正文：列表查询已排除 content，编辑/预览依赖这里补齐
        return toResp(requireNotice(id));
    }

    @Override
    @SensitiveWordFilter
    @Transactional(rollbackFor = Exception.class)
    public void createNotice(NoticeSaveReq req) {
        SysNotice notice = new SysNotice();
        BeanUtils.copyProperties(req, notice);
        notice.setPublishVersion(0L);
        notice.setPublishTime(null);
        if (isDraft(req)) {
            notice.setPublishStatus(DRAFT);
            saveRequired(notice, "新增公告失败");
            return;
        }
        validateSubmission(notice);
        if (notice.getPublishType() == SCHEDULED) {
            validateScheduledTime(notice);
            notice.setPublishStatus(PENDING);
            saveRequired(notice, "新增公告失败");
            noticePublishService.validateDeliveries(requireNotice(notice.getId()));
            return;
        }
        notice.setPublishStatus(DRAFT);
        saveRequired(notice, "新增公告失败");
        SysNotice saved = requireNotice(notice.getId());
        publishCurrent(saved, DRAFT, 0L);
    }

    @Override
    @SensitiveWordFilter
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(Long id, NoticeSaveReq req) {
        SysNotice current = requireNotice(id);
        if (current.getPublishStatus() == PUBLISHED) {
            throw new BusinessException("已发布公告禁止编辑");
        }
        SysNotice update = new SysNotice();
        BeanUtils.copyProperties(req, update);
        update.setId(id);
        update.setTenantId(current.getTenantId());
        update.setPublishVersion(current.getPublishVersion());
        update.setPublishTime(current.getPublishTime());
        if (isDraft(req)) {
            update.setPublishStatus(DRAFT);
            updateRequired(update, current, "公告状态已变化，修改失败");
            return;
        }
        validateSubmission(update);
        if (update.getPublishType() == SCHEDULED) {
            validateScheduledTime(update);
            update.setPublishStatus(PENDING);
            noticePublishService.validateDeliveries(update);
            updateRequired(update, current, "公告状态已变化，修改失败");
            return;
        }
        update.setPublishStatus(current.getPublishStatus());
        noticePublishService.validateDeliveries(update);
        updateRequired(update, current, "公告状态已变化，修改失败");
        publishCurrent(update, current.getPublishStatus(), current.getPublishVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        SysNotice notice = requireNotice(id);
        if (notice.getPublishStatus() == PUBLISHED) {
            throw new BusinessException("公告已发布，禁止重复发布");
        }
        if (notice.getPublishStatus() != DRAFT && notice.getPublishStatus() != PENDING
            && notice.getPublishStatus() != REVOKED) {
            throw new BusinessException("公告当前状态不允许发布");
        }
        validateSubmission(notice);
        publishCurrent(notice, notice.getPublishStatus(), notice.getPublishVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishScheduled(Long id, long expectedVersion) {
        SysNotice notice = requireNotice(id);
        if (notice.getPublishStatus() != PENDING || notice.getPublishVersion() == null
            || notice.getPublishVersion().longValue() != expectedVersion) {
            throw new BusinessException("定时公告状态已变化，发布失败");
        }
        if (notice.getScheduledTime() == null || notice.getScheduledTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("定时公告尚未到发布时间");
        }
        validateSubmission(notice);
        publishCurrent(notice, PENDING, expectedVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        SysNotice notice = requireNotice(id);
        if (notice.getPublishStatus() != PUBLISHED) {
            throw new BusinessException("仅已发布公告允许撤回");
        }
        int affected = baseMapper.revokeCas(id, notice.getPublishVersion(), LocalDateTime.now());
        if (affected != 1) {
            throw new BusinessException("公告状态已变化，撤回失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id) {
        SysNotice notice = requireNotice(id);
        if (notice.getPublishStatus() == PUBLISHED) {
            throw new BusinessException("已发布公告禁止删除");
        }
        long affected = baseMapper.delete(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getId, id)
            // 内存里把 NULL 状态当草稿（见 requireNotice），SQL 必须同口径：
            // 直接 eq 会因 NULL ≠ 0 而永远匹配不到，历史行既发不出去也删不掉
            .apply("COALESCE(publish_status, 0) = {0}", notice.getPublishStatus())
            .eq(SysNotice::getPublishVersion, notice.getPublishVersion()));
        if (affected != 1) {
            throw new BusinessException("公告状态已变化，删除失败");
        }
    }

    private void publishCurrent(SysNotice notice, int expectedStatus, long expectedVersion) {
        long nextVersion = expectedVersion + 1;
        LocalDateTime publishTime = LocalDateTime.now();
        int affected = baseMapper.publishCas(notice.getId(), expectedStatus, expectedVersion,
            nextVersion, publishTime);
        if (affected != 1) {
            throw new BusinessException("公告状态已变化，发布失败");
        }
        notice.setPublishStatus(PUBLISHED);
        notice.setPublishVersion(nextVersion);
        notice.setPublishTime(publishTime);
        noticePublishService.freezeDeliveries(notice, nextVersion);
    }

    private void validateSubmission(SysNotice notice) {
        Integer scope = notice.getNoticeScope();
        if (scope == null || scope < 1 || scope > 4) {
            throw new BusinessException("通知范围不合法");
        }
        if (scope != 1 && (notice.getScopeTargetIds() == null
            || notice.getScopeTargetIds().isBlank())) {
            throw new BusinessException("非全体通知必须选择目标");
        }
        if (notice.getNotifyMethods() == null || notice.getNotifyMethods().isBlank()) {
            throw new BusinessException("通知方式不能为空");
        }
        if (notice.getEffectiveTime() != null && notice.getExpireTime() != null
            && !notice.getEffectiveTime().isBefore(notice.getExpireTime())) {
            throw new BusinessException("生效时间必须早于失效时间");
        }
        if (notice.getPublishType() != IMMEDIATE && notice.getPublishType() != SCHEDULED) {
            throw new BusinessException("发布方式不合法");
        }
    }

    private NoticeResp toResp(SysNotice notice) {
        NoticeResp resp = new NoticeResp();
        BeanUtils.copyProperties(notice, resp);
        return resp;
    }

    private SysNotice requireNotice(Long id) {
        SysNotice notice = getById(id);
        if (notice == null) {
            throw new BusinessException("公告不存在");
        }
        if (notice.getPublishVersion() == null) {
            notice.setPublishVersion(0L);
        }
        if (notice.getPublishStatus() == null) {
            // 历史行可能没有状态（列允许 NULL）：按草稿处理，避免后续 Integer 拆箱比较抛 NPE；
            // 同时与 SQL 的 COALESCE(publish_status, 0) 保持同口径
            notice.setPublishStatus(DRAFT);
        }
        return notice;
    }

    private void saveRequired(SysNotice notice, String message) {
        if (!save(notice)) {
            throw new BusinessException(message);
        }
    }

    private void updateRequired(SysNotice notice, SysNotice current, String message) {
        // 历史行的 publish_status 可能为 NULL（requireNotice 在内存里归一为草稿 0）；
        // 直接 eq(0) 永远匹配不到 NULL，必须与删除/发布/撤回同口径用 COALESCE
        boolean updated = update(notice, new LambdaUpdateWrapper<SysNotice>()
            .eq(SysNotice::getId, current.getId())
            .apply("COALESCE(publish_status, 0) = {0}", current.getPublishStatus())
            .eq(SysNotice::getPublishVersion, current.getPublishVersion()));
        if (!updated) {
            throw new BusinessException(message);
        }
    }

    private void validateScheduledTime(SysNotice notice) {
        if (notice.getScheduledTime() == null || !notice.getScheduledTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("定时发布时间必须晚于当前时间");
        }
    }

    private boolean isDraft(NoticeSaveReq req) {
        return Integer.valueOf(DRAFT).equals(req.getPublishStatus());
    }
}
