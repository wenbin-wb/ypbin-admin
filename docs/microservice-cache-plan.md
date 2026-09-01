# 微服务缓存架构方案（主动失效 + 精确清理）

> 状态：**方案 v3.0（2026-09-01）已实施**——永久缓存 + @CacheEvict 声明式主动失效
> 背景：当前 `SysCache` 固定 TTL 5 分钟，用户编辑/改密后缓存仍是旧值——**改密码后旧缓存密码仍可登录（安全隐患）**、用户资料更新后登录态读到旧数据。参考 blade 的"查询走缓存 + 写操作清缓存"模式重构。

## 一、现状问题

| 问题 | 说明 | 风险 |
|---|---|---|
| 固定 TTL 无主动失效 | `SysCache` 用户缓存 5 分钟，写操作不清 | 改密码后旧缓存可登录（**安全**）；资料更新后读到旧值 |
| 缓存含敏感字段 | `SysUser` 整个实体（含 `password`）入缓存 | 密码明文落 Redis，改密后旧密码仍有效 |
| `evictUser` 定义了但无人调用 | system 侧 5 个写操作（create/update/updateStatus/delete/resetPassword）都不清 | 同上 |
| 只缓存了用户 | 角色码/权限码每次登录都 Feign 查（`listRoleCodes`） | 登录链路多一次 RPC |

## 二、设计原则（对齐 blade，适配 starter）

blade 模式：`CacheUtil.get(域, key, loader)` 查询 + 写操作后 `CacheUtil.clear(域)` **整域清**。
我们的 starter 缓存是**单 key 操作**（无域概念，`CacheUtils.getOrLoad(key, type, loader, ttl)` + `delete(key)`/`delete(keys)`）——**采用精确 key 删除**（比 blade 整域清更精准，不误伤同域其他数据）。

核心三点：
1. **查询走缓存（永久，ttl=null）**：`getOrLoad` 支持 ttl=null 永久不过期，一致性完全由主动失效保证——只要数据未变更永远命中缓存（starter 已增强：`RedisCacheService.getOrLoad` ttl=null 跳过 jitter 直接 set 不过期）
2. **写操作后主动失效**（system 侧写操作调 `SysCache.evictXxx(...)` 精确删 key）
3. **跨服务共享 Redis**：auth/system/ai 同用一个 Redis，system 侧 `CacheUtils.delete` 直接删 key，auth 侧下次 `getOrLoad` 自然回源——**无需通知 auth**（blade 同款：写方清，读方回源）

## 三、缓存键设计

统一前缀 `sys:`，按域分：

```
sys:user:username:{username}    # 按用户名取用户（登录用）
sys:user:id:{userId}            # 按 ID 取用户
sys:role:user:{userId}          # 用户角色码（登录时组装身份头 X-Roles 用）
sys:perm:user:{userId}          # 用户权限码（鉴权用）
```

## 四、SysCache 增强（api 模块）

```java
public final class SysCache {

    // 永久缓存（ttl=null 传 getOrLoad）：一致性由主动失效保证，无时间兜底

    // ---- 用户 ----
    public static SysUser getUserByUsername(String username) { /* getOrLoad + Feign loader */ }
    public static SysUser getUserById(Long userId)            { /* getOrLoad + Feign loader */ }
    public static void evictUser(Long userId, String username) {
        // 精确删：id key + username key（改用户名时两个 key 都要清）
        CacheUtils.delete(List.of(USER_ID_KEY + userId, USERNAME_KEY + username));
    }

    // ---- 角色/权限（登录链路）----
    public static List<String> getUserRoleCodes(Long userId)  { /* getOrLoad + Feign loader */ }
    public static List<String> getUserPermissions(Long userId){ /* getOrLoad + Feign loader */ }
    public static void evictUserAuth(Long userId) {
        CacheUtils.delete(List.of(ROLE_USER_KEY + userId, PERM_USER_KEY + userId));
    }
}
```

**敏感字段处理**：用户缓存**不存密码**——`SysUser` 的 loader 在回填前把 `password` 置空（`user.setPassword(null)`），登录密码校验改为：缓存取用户（无密码）→ 仅用于查 ID/状态 → 密码比对走 system 侧 Feign 专用接口 `verifyPassword(userId, rawPassword)`（system 直查库校验，不在缓存留密码）。这样**缓存永不含密码**，改密后无需等缓存过期即生效。

## 五、写操作清缓存（@CacheEvict 声明式，starter 新增注解）

写操作方法上加 ，方法执行成功后自动清（无需手写 evict 调用，防遗漏）；
键为 SpEL：单引号字符串前缀 +  引用（冒号在字符串内合法），如 。
SpEL 拿不到方法内局部变量时（如 deleteConfig 的 configKey、updateUser 的 normalize 后 phone），保留手动  调用。

### system 侧

| 写操作 | 清缓存 |
|---|---|
| `SysUserServiceImpl.createUser` | `evictUser(null, username)` |
| `SysUserServiceImpl.updateUser` | `evictUser(id, 旧username)` + `evictUserAuth(id)` |
| `SysUserServiceImpl.updateStatus` | `evictUser(id, null)` + `evictUserAuth(id)` |
| `SysUserServiceImpl.deleteUser` | `evictUser(id, username)` + `evictUserAuth(id)` |
| `SysUserServiceImpl.resetPassword` | `evictUser(id, null)`（密码不在缓存，但保险清） |
| 角色/权限变更（`SysUserServiceImpl.assignRoles` 等） | `evictUserAuth(userId)` |
| `SysConfigServiceImpl` create/update/delete | `evictConfig(configKey)` |
| `SocialConfigServiceImpl.updateConfig` | `evictSocialConfig(source)` |
| 社交绑定/解绑（auth 侧 `SocialLoginService`） | `evictSocialBinding(userId, platform, openId)` |

## 六、auth/ai 侧调整

- `AuthService` 登录：`SysCache.getUserByUsername` 拿用户（**无密码**）→ 密码校验改调 `ISystemClient.verifyPassword(userId, raw)`（新增 Feign 接口，system 直查库 `password` 比对）
- `LoginSupport` 组装角色：`SysCache.getUserRoleCodes(userId)`（缓存，不再每次 Feign）
- 高基查询（用户搜索/任务列表等 AI 工具）**不入缓存**（写频繁、非高频读，保持 Feign 直查）

## 七、ISystemClient 新增接口

```
POST /internal/verify-password   R<Boolean> verifyPassword(userId, rawPassword)   # 密码校验（system 直查库）
GET  /internal/role-codes        R<List<String>> listRoleCodes(userId)            # 已有，供 SysCache loader
GET  /internal/permissions       R<List<String>> listPermissions(userId)          # 已有，供 SysCache loader
```

## 八、收益

1. **改密即时生效**：密码不入缓存，改密后立刻生效，无 5 分钟窗口（修复安全隐患）
2. **数据一致性**：用户资料/状态/角色变更后主动清缓存，读侧即时回源，无脏读
3. **登录链路降 RPC**：角色码走缓存，登录少一次 Feign
4. **永久命中**：无 TTL 开销，数据未变更时查询永远走缓存零 RPC；漏清缓存才需要人工/重启恢复（密码永不受影响）

## 九、风险与回退

- 漏清缓存 → 数据滞后直到被清（无 TTL 兜底），需保证写操作清缓存全覆盖；密码除外（永不缓存）
- 跨服务清缓存依赖同一 Redis：部署时确认 auth/system 共用 Redis 实例（docker-compose 已同 Redis）
- 若后续拆分 Redis 实例，改为 system 侧发 Redis Pub/Sub 或消息通知 auth 清（starter 有 `CacheInvalidationPublisher` 可复用）

## 十、实施步骤

1. `SysCache` 增强：不存密码 + 角色/权限缓存 + `evictUser`/`evictUserAuth`
2. `ISystemClient` 新增 `verifyPassword` + `SystemClientImpl` 实现（system 直查库校验）
3. `SysUserServiceImpl`/`SysRoleServiceImpl` 写操作补清缓存
4. `AuthService`/`LoginSupport` 改走新缓存逻辑
5. 编译验证 + 部署手册更新
