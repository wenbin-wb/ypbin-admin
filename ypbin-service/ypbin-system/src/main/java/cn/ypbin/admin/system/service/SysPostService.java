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

import cn.ypbin.admin.system.entity.SysPost;
import cn.ypbin.admin.system.model.req.PostSaveReq;
import cn.ypbin.admin.system.model.resp.PostResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 岗位服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysPostService extends BaseService<SysPost> {

    List<PostResp> listPosts();

    /**
     * 新增岗位。
     *
     * <p><b>返回新岗位 ID</b>：前端新建时要顺带绑定「工作地点（空间）」，
     * 没有 ID 就只能静默丢弃用户的选择（与部门侧同一处理，见 {@code SysDeptService#createDept}）。</p>
     *
     * @param req 请求
     * @return 新岗位 ID
     */
    Long createPost(PostSaveReq req);

    void updatePost(Long id, PostSaveReq req);

    void deletePost(Long id);
}
