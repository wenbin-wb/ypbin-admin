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

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.enums.NoticePublishStatusEnum;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.system.model.resp.NoticeResp;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.admin.system.service.SysNoticeService;
import cn.ypbin.starter.core.exception.BusinessException;
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

    private static final int IMMEDIATE = 1;
    private static final int SCHEDULED = 2;

    private final NoticePublishService noticePublishService;

    @Override
    public List<NoticeResp> listNotices() {
        return list().stream().map(this::toResp).toList();
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
            notice.setPublishStatus(NoticePublishStatusEnum.DRAFT.getCode());
            saveRequired(notice, "新增公告失败");
            return;
        }
        validateSubmission(notice);
        if (Integer.valueOf(SCHEDULED).equals(notice.getPublishType())) {
            validateScheduledTime(notice);
            notice.setPublishStatus(NoticePublishStatusEnum.PENDING.getCode());
            saveRequired(notice, "新增公告失败");
            noticePublishService.validateDeliveries(requireNotice(notice.getId()));
            return;
        }
        notice.setPublishStatus(NoticePublishStatusEnum.DRAFT.getCode());
        saveRequired(notice, "新增公告失败");
        SysNotice saved = requireNotice(notice.getId());
        publishCurrent(saved, NoticePublishStatusEnum.DRAFT.getCode(), 0L);
    }

    @Override
    @SensitiveWordFilter
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(Long id, NoticeSaveReq req) {
        SysNotice current = requireNotice(id);
        if (isStatus(current, NoticePublishStatusEnum.PUBLISHED)) {
            throw new BusinessException("已发布公告禁止编辑");
        }
        SysNotice update = new SysNotice();
        BeanUtils.copyProperties(req, update);
        update.setId(id);
        update.setTenantId(current.getTenantId());
        update.setPublishVersion(current.getPublishVersion());
        update.setPublishTime(current.getPublishTime());
        if (isDraft(req)) {
            update.setPublishStatus(NoticePublishStatusEnum.DRAFT.getCode());
            updateRequired(update, current, "公告状态已变化，修改失败");
            return;
        }
        validateSubmission(update);
        if (Integer.valueOf(SCHEDULED).equals(update.getPublishType())) {
            validateScheduledTime(update);
            update.setPublishStatus(NoticePublishStatusEnum.PENDING.getCode());
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
        if (isStatus(notice, NoticePublishStatusEnum.PUBLISHED)) {
            throw new BusinessException("公告已发布，禁止重复发布");
        }
        if (!isStatus(notice, NoticePublishStatusEnum.DRAFT)
            && !isStatus(notice, NoticePublishStatusEnum.PENDING)
            && !isStatus(notice, NoticePublishStatusEnum.REVOKED)) {
            throw new BusinessException("公告当前状态不允许发布");
        }
        validateSubmission(notice);
        publishCurrent(notice, notice.getPublishStatus(), notice.getPublishVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishScheduled(Long id, long expectedVersion) {
        SysNotice notice = requireNotice(id);
        if (!isStatus(notice, NoticePublishStatusEnum.PENDING)
            || notice.getPublishVersion() == null
            || notice.getPublishVersion().longValue() != expectedVersion) {
            throw new BusinessException("定时公告状态已变化，发布失败");
        }
        if (notice.getScheduledTime() == null || notice.getScheduledTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("定时公告尚未到发布时间");
        }
        validateSubmission(notice);
        publishCurrent(notice, NoticePublishStatusEnum.PENDING.getCode(), expectedVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        SysNotice notice = requireNotice(id);
        if (!isStatus(notice, NoticePublishStatusEnum.PUBLISHED)) {
            throw new BusinessException("仅已发布公告允许撤回");
        }
        int affected = baseMapper.revokeCas(id, NoticePublishStatusEnum.PUBLISHED.getCode(),
            NoticePublishStatusEnum.REVOKED.getCode(), notice.getPublishVersion(),
            LocalDateTime.now());
        if (affected != 1) {
            throw new BusinessException("公告状态已变化，撤回失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id) {
        SysNotice notice = requireNotice(id);
        if (isStatus(notice, NoticePublishStatusEnum.PUBLISHED)) {
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
        int affected = baseMapper.publishCas(notice.getId(), expectedStatus,
            NoticePublishStatusEnum.PUBLISHED.getCode(), expectedVersion, nextVersion, publishTime);
        if (affected != 1) {
            throw new BusinessException("公告状态已变化，发布失败");
        }
        notice.setPublishStatus(NoticePublishStatusEnum.PUBLISHED.getCode());
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
        Integer publishType = notice.getPublishType();
        if (publishType == null || (publishType != IMMEDIATE && publishType != SCHEDULED)) {
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
            // 历史行可能没有状态：按草稿处理，避免后续 Integer 拆箱比较抛 NPE
            notice.setPublishStatus(NoticePublishStatusEnum.DRAFT.getCode());
        }
        return notice;
    }

    /**
     * 判断公告当前状态是否等于给定状态（空值安全）。
     *
     * @param notice   公告
     * @param expected 期望状态
     * @return 是否匹配
     */
    private static boolean isStatus(SysNotice notice, NoticePublishStatusEnum expected) {
        return expected.getCode().equals(notice.getPublishStatus());
    }

    private void saveRequired(SysNotice notice, String message) {
        if (!save(notice)) {
            throw new BusinessException(message);
        }
    }

    private void updateRequired(SysNotice notice, SysNotice current, String message) {
        boolean updated = update(notice, new LambdaUpdateWrapper<SysNotice>()
            .eq(SysNotice::getId, current.getId())
            .eq(SysNotice::getPublishStatus, current.getPublishStatus())
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
        return NoticePublishStatusEnum.DRAFT.getCode().equals(req.getPublishStatus());
    }
}
