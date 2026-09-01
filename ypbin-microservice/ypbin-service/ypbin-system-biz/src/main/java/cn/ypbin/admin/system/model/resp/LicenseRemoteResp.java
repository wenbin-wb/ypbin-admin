/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import lombok.Getter;
import lombok.Setter;
/**
 * 联机校验响应。
 *
 * <p>供消费端联机校验使用：{@code valid=false} 表示授权当前不可用（被吊销/非已签发/指纹不符/鉴权失败），
 * 消费端应据此阻断；{@code valid=true} 表示放行。判定信号只有这一个，消费端不做额外猜测。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Getter
@Setter
public class LicenseRemoteResp {

    /** 授权当前是否有效可用 */
    private boolean valid;

    /** 稳定判定码 */
    private String reasonCode;

    /** 判定说明 */
    private String reason;

    /**
     * 有效响应。
     *
     * @param reasonCode 判定码
     * @param reason     判定说明
     * @return 有效响应
     */
    public static LicenseRemoteResp valid(String reasonCode, String reason) {
        LicenseRemoteResp resp = new LicenseRemoteResp();
        resp.setValid(true);
        resp.setReasonCode(reasonCode);
        resp.setReason(reason);
        return resp;
    }

    /**
     * 无效响应。
     *
     * @param reasonCode 判定码
     * @param reason     无效原因
     * @return 无效响应
     */
    public static LicenseRemoteResp invalid(String reasonCode, String reason) {
        LicenseRemoteResp resp = new LicenseRemoteResp();
        resp.setValid(false);
        resp.setReasonCode(reasonCode);
        resp.setReason(reason);
        return resp;
    }
}
