/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/**
 * 用户批量导入结果。
 *
 * @author wenbin
 * @since 2026-08-27
 */
@Getter
@Setter
public class UserImportResult implements Serializable {

    /** 总条数 */
    private int totalCount;

    /** 成功条数 */
    private int successCount;

    /** 失败条数 */
    private int failureCount;

    /** 失败原因列表 */
    private List<String> failureMessages = new ArrayList<>();
}
