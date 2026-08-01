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

import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.model.query.ConfigQuery;
import cn.ypbin.admin.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.system.model.resp.ConfigResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 系统参数服务。提供带本地缓存的类型化取值，供密码策略、登录开关、短信/邮件等模块读取。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysConfigService extends BaseService<SysConfig> {

    /**
     * 取字符串参数。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    String getString(String key, String defaultValue);

    /**
     * 取布尔参数（"true"/"1"/"on"/"yes" 视为真）。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * 取整数参数。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    int getInt(String key, int defaultValue);

    /**
     * 分页查询系统参数。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ConfigResp> pageConfigs(ConfigQuery query);

    /**
     * 按分组查询系统参数（配置页面按组加载）。
     *
     * @param configGroup 分组
     * @return 参数列表
     */
    List<ConfigResp> listByGroup(String configGroup);

    /**
     * 新增参数（键查重）。
     *
     * @param req 请求
     */
    void createConfig(ConfigSaveReq req);

    /**
     * 编辑参数（键查重排除自身）。
     *
     * @param id  参数 ID
     * @param req 请求
     */
    void updateConfig(Long id, ConfigSaveReq req);

    /**
     * 删除参数（内置参数不可删）。
     *
     * @param id 参数 ID
     */
    void deleteConfig(Long id);

    /**
     * 按键值对批量更新参数（配置页面保存）。
     *
     * @param req 批量更新请求
     */
    void updateBatch(ConfigUpdateBatchReq req);

    /**
     * 刷新本地参数缓存。
     */
    void refreshCache();
}
