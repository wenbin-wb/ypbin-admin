/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

/**
 * 系统参数提交完成后的同步事件。
 *
 * @param smsChanged 短信配置是否发生变化
 * @author wenbin
 * @since 2026-08-08
 */
public record ConfigChangedEvent(boolean smsChanged) {
}
