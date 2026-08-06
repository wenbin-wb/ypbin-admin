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

import cn.ypbin.admin.common.config.LicenseIssuerProperties;
import cn.ypbin.admin.system.entity.SysApp;
import cn.ypbin.admin.system.entity.SysLicense;
import cn.ypbin.admin.system.mapper.SysLicenseMapper;
import cn.ypbin.admin.system.model.query.LicenseQuery;
import cn.ypbin.admin.system.model.req.LicenseApproveReq;
import cn.ypbin.admin.system.model.req.LicenseSaveReq;
import cn.ypbin.admin.system.model.resp.LicenseDeliveryResp;
import cn.ypbin.admin.system.model.resp.LicenseKeyPairResp;
import cn.ypbin.admin.system.model.resp.LicenseRemoteResp;
import cn.ypbin.admin.system.model.resp.LicenseResp;
import cn.ypbin.admin.system.service.SysAppService;
import cn.ypbin.admin.system.service.SysLicenseService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.LicenseSigner;
import cn.ypbin.starter.license.core.LicenseStatus;
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 商业授权签发与管理服务实现。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Service
@RequiredArgsConstructor
public class SysLicenseServiceImpl extends BaseServiceImpl<SysLicenseMapper, SysLicense>
    implements SysLicenseService {

    /** 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 待审批 */
    private static final String STATUS_PENDING = "PENDING";
    /** 已签发 */
    private static final String STATUS_ISSUED = "ISSUED";
    /** 已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";
    /** 已吊销 */
    private static final String STATUS_REVOKED = "REVOKED";

    /** 交付模式：内联授权码 */
    private static final String DELIVERY_CODE = "CODE";
    /** 交付模式：授权文件 */
    private static final String DELIVERY_FILE = "FILE";

    private final LicenseIssuerProperties issuerProperties;
    private final SysAppService sysAppService;

    @Override
    public PageResult<LicenseResp> pageLicense(LicenseQuery query) {
        PageResult<SysLicense> source = page(query, new LambdaQueryWrapper<SysLicense>()
            .like(StringUtils.hasText(query.getSubject()), SysLicense::getSubject, query.getSubject())
            .eq(StringUtils.hasText(query.getApproveStatus()), SysLicense::getApproveStatus,
                query.getApproveStatus())
            .eq(StringUtils.hasText(query.getTenantId()), SysLicense::getTenantId, query.getTenantId())
            .orderByDesc(SysLicense::getCreateTime));
        List<LicenseResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public LicenseResp getLicense(Long id) {
        return toResp(getExisting(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(LicenseSaveReq req) {
        checkDeliveryMode(req.getDeliveryMode());
        SysLicense entity = new SysLicense();
        BeanUtils.copyProperties(req, entity);
        entity.setApproveStatus(STATUS_DRAFT);
        // 当前仅手工签发；支付自动获取授权为预留能力（source=payment），落地前固定手工来源
        entity.setSource("manual");
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, LicenseSaveReq req) {
        SysLicense entity = getExisting(id);
        if (!STATUS_DRAFT.equals(entity.getApproveStatus())
            && !STATUS_REJECTED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可编辑");
        }
        checkDeliveryMode(req.getDeliveryMode());
        BeanUtils.copyProperties(req, entity);
        entity.setId(id);
        entity.setApproveStatus(STATUS_DRAFT);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        SysLicense entity = getExisting(id);
        if (!STATUS_DRAFT.equals(entity.getApproveStatus())
            && !STATUS_REJECTED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交审批");
        }
        SysLicense update = new SysLicense();
        update.setId(id);
        update.setApproveStatus(STATUS_PENDING);
        update.setRejectReason("");
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, LicenseApproveReq req, Long operator) {
        SysLicense entity = getExisting(id);
        if (!STATUS_PENDING.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅待审批状态可审批");
        }
        if (operator == null) {
            throw new BusinessException("无法确认审批人身份");
        }
        if (operator.equals(entity.getCreateUser())) {
            throw new BusinessException("审批人不能是授权创建人，须由他人复核签发");
        }
        SysLicense update = new SysLicense();
        update.setId(id);
        update.setApproveUser(operator);
        update.setApproveTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(req.getApprove())) {
            issue(entity, update);
        } else {
            if (!StringUtils.hasText(req.getRejectReason())) {
                throw new BusinessException("驳回时必须填写驳回原因");
            }
            update.setApproveStatus(STATUS_REJECTED);
            update.setRejectReason(req.getRejectReason());
        }
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        SysLicense entity = getExisting(id);
        if (!STATUS_ISSUED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅已签发状态可吊销");
        }
        SysLicense update = new SysLicense();
        update.setId(id);
        update.setApproveStatus(STATUS_REVOKED);
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLicense(Long id) {
        SysLicense entity = getExisting(id);
        if (!STATUS_DRAFT.equals(entity.getApproveStatus())
            && !STATUS_REJECTED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可删除");
        }
        removeById(id);
    }

    @Override
    public LicenseKeyPairResp generateKeyPair() {
        KeyPairBase64 pair = Sm2Utils.generateKeyPair();
        return new LicenseKeyPairResp(pair.publicKey(), pair.privateKey(), Sm4Utils.generateKeyBase64());
    }

    @Override
    public SysLicense loadForDownload(Long id) {
        SysLicense entity = getExisting(id);
        if (!STATUS_ISSUED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅已签发状态可下载授权文件");
        }
        if (!DELIVERY_FILE.equals(entity.getDeliveryMode())) {
            throw new BusinessException("该授权为内联授权码交付，请直接复制授权码");
        }
        return entity;
    }

    @Override
    public LicenseDeliveryResp getDelivery(Long id) {
        SysLicense entity = getExisting(id);
        if (!STATUS_ISSUED.equals(entity.getApproveStatus())) {
            throw new BusinessException("仅已签发状态可查看交付信息");
        }
        LicenseDeliveryResp resp = new LicenseDeliveryResp();
        resp.setAuthCode(entity.getAuthCode());
        if (entity.getAppId() != null) {
            SysApp app = sysAppService.getById(entity.getAppId());
            if (app != null) {
                resp.setAppId(app.getId());
                resp.setAppName(app.getAppName());
                resp.setAccessKey(app.getAccessKey());
                resp.setSecretKey(app.getSecretKey());
            }
        }
        return resp;
    }

    @Override
    public LicenseRemoteResp verifyRemote(String licenseId, String fingerprint) {
        SysLicense entity = getOne(new LambdaQueryWrapper<SysLicense>()
            .eq(SysLicense::getLicenseId, licenseId), false);
        if (entity == null) {
            return LicenseRemoteResp.invalid("授权不存在：" + licenseId);
        }
        if (!STATUS_ISSUED.equals(entity.getApproveStatus())) {
            return LicenseRemoteResp.invalid("授权状态不可用（" + entity.getApproveStatus()
                + "），可能已被吊销");
        }
        if (StringUtils.hasText(fingerprint)
            && !toContent(entity, entity.getLicenseId()).matchesFingerprint(fingerprint)) {
            return LicenseRemoteResp.invalid("机器指纹不匹配");
        }
        return LicenseRemoteResp.valid();
    }

    /**
     * 签发：用配置托管的私钥与 SM4 密钥对授权内容签名加密，产出授权串写入更新对象。
     *
     * @param entity 原授权记录
     * @param update 待更新对象（承载签发产物）
     */
    private void issue(SysLicense entity, SysLicense update) {
        String privateKey = issuerProperties.getPrivateKey();
        String sm4Key = issuerProperties.getSm4Key();
        if (!StringUtils.hasText(privateKey) || !StringUtils.hasText(sm4Key)) {
            throw new BusinessException("签发密钥未配置，请先生成密钥对并写入部署环境");
        }
        String licenseId = UUID.randomUUID().toString().replace("-", "");
        LicenseContent content = toContent(entity, licenseId);
        String authCode = LicenseSigner.issue(content, privateKey, sm4Key);
        if (DELIVERY_CODE.equals(entity.getDeliveryMode())
            && authCode.length() > LicenseSigner.MAX_AUTH_CODE_LENGTH) {
            throw new BusinessException("授权信息过多，内联授权码超出 "
                + LicenseSigner.MAX_AUTH_CODE_LENGTH + " 字符上限，请改用授权文件交付");
        }
        update.setLicenseId(licenseId);
        update.setAuthCode(authCode);
        update.setApproveStatus(STATUS_ISSUED);
        ensureOnlineApp(entity, update);
    }

    /**
     * 签发时确保联机开放应用存在：同一被授权方复用已有应用，否则按被授权方自动新建。
     * 应用只是联机请求的传输凭据，吊销授权不连带禁用应用（有效性由授权状态本身判定），
     * 避免误伤同被授权方的其他授权。
     *
     * @param entity 授权记录
     * @param update 待更新对象（写入联机应用 ID）
     */
    private void ensureOnlineApp(SysLicense entity, SysLicense update) {
        SysApp app = sysAppService.getOne(new LambdaQueryWrapper<SysApp>()
            .eq(SysApp::getAppName, entity.getSubject())
            .eq(SysApp::getEnabled, 1)
            .last("LIMIT 1"), false);
        if (app == null) {
            app = new SysApp();
            app.setAppName(entity.getSubject());
            app.setAccessKey(generateAppKey());
            app.setSecretKey(generateAppKey());
            app.setEnabled(1);
            sysAppService.save(app);
        }
        update.setAppId(app.getId());
    }

    /**
     * 生成开放应用密钥（32 位无横线 UUID hex）。
     *
     * @return 新密钥
     */
    private String generateAppKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 组装 starter 授权内容载体。签发时传入新生成的授权编号，展示时传入已存编号。
     *
     * @param entity    授权记录
     * @param licenseId 授权编号
     * @return 授权内容
     */
    private LicenseContent toContent(SysLicense entity, String licenseId) {
        return new LicenseContent(
            licenseId,
            entity.getSubject(),
            entity.getRemark(),
            entity.getFingerprints(),
            entity.getTenantId(),
            LocalDateTime.now(),
            entity.getEffectiveAt(),
            entity.getExpireAt(),
            entity.getGraceDays() == null ? 0 : entity.getGraceDays(),
            entity.getModules(),
            entity.getQuotas(),
            entity.getAttributes());
    }

    /**
     * 校验交付模式取值。
     *
     * @param deliveryMode 交付模式
     */
    private void checkDeliveryMode(String deliveryMode) {
        if (!DELIVERY_CODE.equals(deliveryMode) && !DELIVERY_FILE.equals(deliveryMode)) {
            throw new BusinessException("交付模式仅支持 CODE 或 FILE");
        }
    }

    /**
     * 按主键取授权，不存在则暴露业务错误。
     *
     * @param id 授权主键
     * @return 授权记录
     */
    private SysLicense getExisting(Long id) {
        SysLicense entity = getById(id);
        if (entity == null) {
            throw new BusinessException("授权不存在");
        }
        return entity;
    }

    /**
     * 转换为响应对象，并按当前时间为已签发授权计算实时运行态。
     *
     * @param entity 授权记录
     * @return 响应对象
     */
    private LicenseResp toResp(SysLicense entity) {
        LicenseResp resp = new LicenseResp();
        BeanUtils.copyProperties(entity, resp);
        if (STATUS_ISSUED.equals(entity.getApproveStatus())) {
            LicenseStatus status = LicenseManager.evaluateAt(
                toContent(entity, entity.getLicenseId()), LocalDateTime.now());
            resp.setCurrentStatus(status.getDescription());
        }
        return resp;
    }
}
