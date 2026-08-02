/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.provider;

import cn.ypbin.starter.log.core.IpLocationResolver;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;
import org.springframework.stereotype.Component;

/**
 * IP 归属地解析：实现 starter 的 {@link IpLocationResolver} 扩展点，接入 ip2region 离线库。
 *
 * <p>本地/内网 IP 返回“内网”；无法解析时返回 null（starter 侧留空、不影响其它日志字段）。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
public class Ip2regionLocationResolver implements IpLocationResolver {

    private final Ip2regionSearcher searcher;

    public Ip2regionLocationResolver(Ip2regionSearcher searcher) {
        this.searcher = searcher;
    }

    @Override
    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        // 本机/内网回环地址
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)
            || "localhost".equalsIgnoreCase(ip)) {
            return "内网";
        }
        IpInfo info = searcher.memorySearch(ip);
        if (info == null) {
            return null;
        }
        // 优先返回精简的省市地址；ip2region 数据为 “国家|区域|省|市|ISP”
        String address = info.getAddressAndIsp();
        return (address == null || address.isBlank()) ? null : address;
    }
}
