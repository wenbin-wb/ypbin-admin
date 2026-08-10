/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.annotation.PlatformAccess;
import cn.ypbin.admin.system.service.SysFileService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.storage.model.FileInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link UserProfileController} 个人头像上传测试。
 *
 * @author wenbin
 * @since 2026-08-10
 */
class UserProfileControllerTest {

    @Test
    void uploadAvatarUsesAvatarModuleAndSkipsPlatformGuard() {
        SysFileService fileService = mock(SysFileService.class);
        UserProfileController controller = new UserProfileController(mock(SysUserService.class), fileService);
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        FileInfo info = new FileInfo();
        when(fileService.uploadFile(file, "avatar")).thenReturn(info);

        R<FileInfo> result = controller.uploadAvatar(file);

        assertThat(result.getData()).isSameAs(info);
        verify(fileService).uploadFile(file, "avatar");
        assertThat(UserProfileController.class.getAnnotation(PlatformAccess.class)).isNull();
    }
}
