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
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.model.req.ChangePasswordReq;
import cn.ypbin.admin.modules.system.model.req.ProfileUpdateReq;
import cn.ypbin.admin.modules.system.model.resp.ProfileResp;

/**
 * 个人中心服务。操作对象恒为当前登录用户。
 *
 * @author wenbin
 * @since 2026-08-31
 */
public interface UserProfileService {

    ProfileResp getProfile();

    void updateProfile(ProfileUpdateReq req);

    void changePassword(ChangePasswordReq req);
}
