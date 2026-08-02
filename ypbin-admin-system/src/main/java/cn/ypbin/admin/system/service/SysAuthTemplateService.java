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

import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.model.req.AuthTemplateSaveReq;
import cn.ypbin.admin.system.model.resp.AuthTemplateResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;
import java.util.Set;

/**
 * 权限模板服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysAuthTemplateService extends BaseService<SysAuthTemplate> {

    /**
     * 查询全部权限模板（含菜单授权，列表/下拉用）。
     *
     * @return 模板列表
     */
    List<AuthTemplateResp> listTemplates();

    /**
     * 新增模板（编码查重 + 授权菜单）。
     *
     * @param req 请求
     */
    void createTemplate(AuthTemplateSaveReq req);

    /**
     * 编辑模板（编码查重排除自身 + 重授权菜单）。
     *
     * @param id  模板 ID
     * @param req 请求
     */
    void updateTemplate(Long id, AuthTemplateSaveReq req);

    /**
     * 删除模板（清理菜单关联）。
     *
     * @param id 模板 ID
     */
    void deleteTemplate(Long id);

    /**
     * 查询指定模板授权的菜单 ID 集合。
     *
     * @param templateId 模板 ID
     * @return 菜单 ID 集合
     */
    Set<Long> listMenuIds(Long templateId);

    /**
     * 解析租户可见的菜单 ID 集合：按租户所属权限模板计算。
     *
     * @param tenantId 租户 ID，为 null 表示平台（不按模板过滤）
     * @return 允许的菜单 ID 集合；返回 null 表示不过滤（全部可见）
     */
    Set<Long> resolveTenantMenuIds(Long tenantId);
}
