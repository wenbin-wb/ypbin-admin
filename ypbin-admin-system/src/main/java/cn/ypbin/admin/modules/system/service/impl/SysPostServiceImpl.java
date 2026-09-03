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

import cn.ypbin.admin.modules.system.entity.SysPost;
import cn.ypbin.admin.modules.system.mapper.SysPostMapper;
import cn.ypbin.admin.modules.system.model.req.PostSaveReq;
import cn.ypbin.admin.modules.system.model.resp.PostResp;
import cn.ypbin.admin.modules.system.service.SysPostService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位服务实现。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class SysPostServiceImpl extends BaseServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    @Override
    public List<PostResp> listPosts() {
        return list(new LambdaQueryWrapper<SysPost>().orderByAsc(SysPost::getSort))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPost(PostSaveReq req) {
        checkNameUnique(req.getName(), null);
        checkCodeUnique(req.getCode(), null);
        SysPost post = new SysPost();
        BeanUtils.copyProperties(req, post);
        post.setTenantId(currentTenantId());
        if (!save(post)) {
            throw new BusinessException("岗位创建失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long id, PostSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("岗位不存在或无权操作");
        }
        checkNameUnique(req.getName(), id);
        checkCodeUnique(req.getCode(), id);
        SysPost post = new SysPost();
        BeanUtils.copyProperties(req, post);
        post.setId(id);
        if (!updateById(post)) {
            throw new BusinessException("岗位更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("岗位删除失败");
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysPost>()
            .eq(SysPost::getName, name)
            .ne(excludeId != null, SysPost::getId, excludeId));
        if (exists) {
            throw new BusinessException("岗位名称已存在：" + name);
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysPost>()
            .eq(SysPost::getCode, code)
            .ne(excludeId != null, SysPost::getId, excludeId));
        if (exists) {
            throw new BusinessException("岗位编码已存在：" + code);
        }
    }

    private Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户"));
    }

    private PostResp toResp(SysPost post) {
        PostResp resp = new PostResp();
        BeanUtils.copyProperties(post, resp);
        return resp;
    }
}
