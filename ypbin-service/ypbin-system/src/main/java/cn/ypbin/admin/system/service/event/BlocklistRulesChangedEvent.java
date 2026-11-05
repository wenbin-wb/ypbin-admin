/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.event;

/**
 * 黑名单规则已变更。事务提交后触发全量下发，避免 Redis 先于数据库生效。
 *
 * @author wenbin
 * @since 2026-11-05
 */
public record BlocklistRulesChangedEvent() {
}
