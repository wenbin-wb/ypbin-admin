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

import lombok.Data;

/**
 * 联机校验响应。
 *
 * <p>供消费端联机校验使用：{@code valid=false} 表示授权当前不可用（被吊销/非已签发/指纹不符/鉴权失败），
 * 消费端应据此阻断；{@code valid=true} 表示放行。判定信号只有这一个，消费端不做额外猜测。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Data
public class LicenseRemoteResp {

    /** 授权当前是否有效可用 */
    private boolean valid;

    /** 判定说明（无效时的原因，供消费端日志/提示） */
    private String reason;

    /**
     * 有效响应。
     *
     * @return 有效响应
     */
    public static LicenseRemoteResp valid() {
        LicenseRemoteResp resp = new LicenseRemoteResp();
        resp.setValid(true);
        resp.setReason("ok");
        return resp;
    }

    /**
     * 无效响应。
     *
     * @param reason 无效原因
     * @return 无效响应
     */
    public static LicenseRemoteResp invalid(String reason) {
        LicenseRemoteResp resp = new LicenseRemoteResp();
        resp.setValid(false);
        resp.setReason(reason);
        return resp;
    }
}
