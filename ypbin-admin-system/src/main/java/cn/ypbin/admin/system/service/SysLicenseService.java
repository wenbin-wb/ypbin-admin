/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysLicense;
import cn.ypbin.admin.system.model.query.LicenseQuery;
import cn.ypbin.admin.system.model.req.LicenseApproveReq;
import cn.ypbin.admin.system.model.req.LicenseSaveReq;
import cn.ypbin.admin.system.model.resp.LicenseDeliveryResp;
import cn.ypbin.admin.system.model.resp.LicenseIssueResp;
import cn.ypbin.admin.system.model.resp.LicenseKeyPairResp;
import cn.ypbin.admin.system.model.resp.LicenseRemoteResp;
import cn.ypbin.admin.system.model.resp.LicenseResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 商业授权签发与管理服务。
 *
 * <p>承载授权从草稿到签发、吊销的完整生命周期流转：草稿仅创建人可改；提交后进入待审批；
 * 审批通过则用配置托管的签发密钥完成国密签名加密、产出授权串；驳回退回草稿态；已签发可吊销。
 * 审批人须不同于创建人，实现双人把关。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public interface SysLicenseService extends BaseService<SysLicense> {

    /**
     * 分页查询授权，出参附带按当前时间实时计算的运行态。
     *
     * @param query 查询条件
     * @return 授权分页
     */
    PageResult<LicenseResp> pageLicense(LicenseQuery query);

    /**
     * 查询授权详情（含实时运行态）。
     *
     * @param id 授权主键
     * @return 授权详情
     */
    LicenseResp getLicense(Long id);

    /**
     * 创建授权草稿。
     *
     * @param req 授权内容
     * @return 新建草稿主键
     */
    Long createDraft(LicenseSaveReq req);

    /**
     * 编辑授权草稿（仅草稿或已驳回状态可改）。
     *
     * @param id  授权主键
     * @param req 授权内容
     */
    void updateDraft(Long id, LicenseSaveReq req);

    /**
     * 提交审批（草稿/已驳回 → 待审批）。
     *
     * @param id 授权主键
     */
    void submit(Long id);

    /**
     * 审批（待审批 → 已签发 / 已驳回）。审批人须不同于创建人。
     *
     * @param id        授权主键
     * @param req       审批结论
     * @param operator  审批操作人
     * @return 通过时返回签发结果，驳回时为空
     */
    LicenseIssueResp approve(Long id, LicenseApproveReq req, Long operator);

    /**
     * 吊销授权（已签发 → 已吊销）。
     *
     * @param id 授权主键
     */
    void revoke(Long id);

    /**
     * 删除授权（仅草稿/已驳回状态可删）。
     *
     * @param id 授权主键
     */
    void deleteLicense(Long id);

    /**
     * 生成一套签发密钥对（SM2 公私钥 + SM4 密钥），不落库，仅返回一次供写入部署环境。
     *
     * @return 新生成的密钥对
     */
    LicenseKeyPairResp generateKeyPair();

    /**
     * 取授权串用于文件下载（仅已签发的授权可下载）。
     *
     * @param id 授权主键
     * @return 授权对象（含 authCode 与被授权方名称）
     */
    SysLicense loadForDownload(Long id);

    /**
     * 取授权交付信息（授权码与联机应用公开标识，仅已签发的授权可用）。
     *
     * @param id 授权主键
     * @return 不含应用密钥的交付信息
     */
    LicenseDeliveryResp getDelivery(Long id);

    /**
     * 联机校验授权状态（供消费端回验吊销等在线约束）。
     *
     * <p>鉴权由接口签名完成：请求须携带开放应用 AK/SK 签名（accessKey/timestamp/nonce/sign），
     * 在 controller 层经 {@code SignChecker} 校验通过后才进入本方法。</p>
     *
     * @param licenseId   授权编号
     * @param fingerprint 消费端机器指纹
     * @param accessKey   已认证的应用标识
     * @return 联机校验结果（无效时携带稳定原因码）
     */
    LicenseRemoteResp verifyRemote(String licenseId, String fingerprint, String accessKey);
}
