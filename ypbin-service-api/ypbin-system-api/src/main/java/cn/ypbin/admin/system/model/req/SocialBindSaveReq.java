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
package cn.ypbin.admin.system.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 新增第三方账号绑定请求（内部通道）。
 *
 * <p>accessToken 属于可取用凭据，必须走请求体而非 URL 查询串。</p>
 *
 * @author wenbin
 * @since 2026-11-01
 */
@Getter
@Setter
public class SocialBindSaveReq {

    /** 用户 ID */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 平台标识 */
    @NotBlank(message = "平台标识不能为空")
    private String platform;

    /** 第三方 openId */
    @NotBlank(message = "openId 不能为空")
    private String openId;

    /** 第三方昵称 */
    private String nickname;

    /** 第三方头像 */
    private String avatar;

    /** 第三方 accessToken（可空） */
    private String accessToken;
}
