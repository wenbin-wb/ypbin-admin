/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.ai.entity.AiPromptTemplate;
import cn.ypbin.admin.system.ai.mapper.AiPromptTemplateMapper;
import cn.ypbin.admin.system.ai.model.req.AiPromptTemplateSaveReq;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/prompt-templates")
@RequiredArgsConstructor
public class AiPromptTemplateController extends BaseController {

    private final AiPromptTemplateMapper templateMapper;

    @GetMapping
    @SaCheckPermission("ai:prompt:list")
    public R<List<AiPromptTemplate>> list() {
        Integer tenantId = currentTenantId();
        return ok(templateMapper.selectList(
            new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getTenantId, tenantId)
                .eq(AiPromptTemplate::getStatus, 1)
                .orderByDesc(AiPromptTemplate::getCreateTime)));
    }

    @PostMapping
    @SaCheckPermission("ai:prompt:create")
    public R<Void> create(@Valid @RequestBody AiPromptTemplateSaveReq req) {
        Integer tenantId = currentTenantId();
        AiPromptTemplate tpl = new AiPromptTemplate();
        BeanUtils.copyProperties(req, tpl);
        tpl.setTenantId(tenantId);
        templateMapper.insert(tpl);
        return ok();
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ai:prompt:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AiPromptTemplateSaveReq req) {
        AiPromptTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        BeanUtils.copyProperties(req, existing, "id", "tenantId", "status");
        templateMapper.updateById(existing);
        return ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:prompt:delete")
    public R<Void> delete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return ok();
    }

    /** 更新状态（启用/停用）*/
    @PutMapping("/{id}/status/{status}")
    @SaCheckPermission("ai:prompt:edit")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        AiPromptTemplate tpl = new AiPromptTemplate();
        tpl.setId(id);
        tpl.setStatus(status);
        templateMapper.updateById(tpl);
        return ok();
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Integer currentTenantId() {
        return UserContext.getTenantId()
            .map(Long::intValue)
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
