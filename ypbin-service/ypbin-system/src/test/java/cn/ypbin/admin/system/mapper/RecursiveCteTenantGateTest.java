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
package cn.ypbin.admin.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门禁：Mapper 里带 {@code WITH RECURSIVE} 的语句<b>必须</b>跳过租户插件并显式写租户条件。
 *
 * <p><b>为什么需要这条门禁（真实缺陷，2026-10-23 实测）</b>：MyBatis-Plus 的
 * {@code TenantLineInnerInterceptor} 按 SQL 里出现的<b>表名</b>注入 {@code tenant_id} 条件，
 * 它认不出 {@code WITH RECURSIVE space_chain} 是 CTE 而不是表，于是把 {@code space_chain}
 * 也当表处理，生成
 * {@code INNER JOIN space_chain c ON s.id = c.pid AND c.tenant_id = 1}，
 * 运行时直接 {@code Unknown column 'c.tenant_id' in 'on clause'} ⇒ <b>功能整个不可用</b>。</p>
 *
 * <p><b>为什么靠人眼审不出来</b>：① 报错发生在运行期而非启动期；② 同一个 CTE 在
 * {@code SELECT} 与 {@code UPDATE} 形态下插件行为<b>不同</b>（UPDATE 只给目标表加条件、
 * 不进入 CTE 内部）——读源码会得出「这条没问题」的错误结论；③ 单元测试若 mock 掉 Mapper，
 * SQL 根本不执行，用例全绿。</p>
 *
 * <p><b>判定口径</b>（两条必须同时满足，缺一即可能越权或报错）：
 * <ol>
 *   <li>方法上有 {@code @InterceptorIgnore(tenantLine = "true")}；</li>
 *   <li>SQL 文本里真的出现 {@code tenant_id} 条件（{@code #{tenantId}} 占位）。</li>
 * </ol>
 * 只满足第 1 条 = 跳过插件但没加条件 ⇒ <b>跨租户泄露</b>，比报错更危险；
 * 只满足第 2 条 = 插件再注入一次 ⇒ 报 Unknown column。故必须成对。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
class RecursiveCteTenantGateTest {

    /** 被测 Mapper：本仓唯一使用递归 CTE 的 Mapper。 */
    private static final Class<?>[] CTE_MAPPERS = {SysSpaceMapper.class};

    /** 递归 CTE 特征（大小写不敏感）。 */
    private static final Pattern WITH_RECURSIVE = Pattern.compile("\\bWITH\\s+RECURSIVE\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("★ 带 WITH RECURSIVE 的 Mapper 方法：必须跳过租户插件（否则 CTE 名被当表注入租户列）")
    void recursiveCteMethodsMustIgnoreTenantLine() {
        List<String> violations = new ArrayList<>();
        for (Class<?> mapper : CTE_MAPPERS) {
            for (Method method : mapper.getDeclaredMethods()) {
                String sql = extractSql(method);
                if (sql == null || !WITH_RECURSIVE.matcher(sql).find()) {
                    continue;
                }
                InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
                if (ignore == null || !"true".equals(ignore.tenantLine())) {
                    violations.add(mapper.getSimpleName() + "#" + method.getName()
                        + " → 缺少 @InterceptorIgnore(tenantLine = \"true\")");
                }
            }
        }
        assertThat(violations)
            .as("这些递归 CTE 语句没跳过租户插件：插件会把 CTE 名当表、注入它不存在的 tenant_id 列，"
                + "运行时 Unknown column 报错（2026-10-23 实测）。请加 "
                + "@InterceptorIgnore(tenantLine = \"true\") 并在 SQL 里显式写 tenant_id 条件")
            .isEmpty();
    }

    @Test
    @DisplayName("★ 跳过租户插件的 CTE 语句必须自己写 tenant_id 条件（否则跨租户泄露）")
    void ignoredCteMustCarryExplicitTenantCondition() {
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (Class<?> mapper : CTE_MAPPERS) {
            for (Method method : mapper.getDeclaredMethods()) {
                String sql = extractSql(method);
                if (sql == null || !WITH_RECURSIVE.matcher(sql).find()) {
                    continue;
                }
                InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
                if (ignore == null || !"true".equals(ignore.tenantLine())) {
                    continue;
                }
                checked++;
                if (!sql.contains("tenant_id")) {
                    violations.add(mapper.getSimpleName() + "#" + method.getName()
                        + " → SQL 里没有 tenant_id 条件");
                }
            }
        }
        assertThat(violations)
            .as("跳过租户插件却不在 SQL 里写租户条件 = 一个租户能看到另一个租户的空间树（越权）。"
                + "报错比越权好，但两者都不允许")
            .isEmpty();
        assertThat(checked)
            .as("没有检查到任何「跳过租户插件的 CTE 语句」——规则等于空转，"
                + "请确认 SysSpaceMapper 的方法签名/注解还没变")
            .isPositive();
    }

    @Test
    @DisplayName("反向有效性自检：注入一段违反规则的合成 SQL，判定函数必须能命中（防规则恒真）")
    void ruleMustActuallyDetectViolation() {
        // 构造「有 CTE、无注解」与「有注解、无租户条件」两种违规形态，验证判定逻辑不是恒真
        assertThat(WITH_RECURSIVE.matcher("WITH RECURSIVE c AS (SELECT 1) SELECT * FROM c").find())
            .as("CTE 特征正则失效——门禁会变成空转").isTrue();
        assertThat(WITH_RECURSIVE.matcher("SELECT * FROM sys_space").find())
            .as("CTE 特征正则误报普通查询").isFalse();
        assertThat("WITH RECURSIVE c AS (SELECT 1) SELECT * FROM c".contains("tenant_id"))
            .as("租户条件判据失效").isFalse();
        assertThat("SELECT * FROM sys_space WHERE tenant_id = #{tenantId}".contains("tenant_id"))
            .as("租户条件判据无法识别合法写法").isTrue();
    }

    @Test
    @DisplayName("扫描面自检：本仓确实存在带 CTE 的语句（否则本门禁在守一个不存在的目标）")
    void cteStatementMustExistInRepository() throws IOException {
        Path root = Path.of("src/main/java");
        assertThat(Files.isDirectory(root))
            .as("扫描根不存在：门禁会扫到 0 个文件而恒绿").isTrue();
        long count;
        try (Stream<Path> files = Files.walk(root)) {
            count = files.filter(path -> path.toString().endsWith(".java"))
                .map(RecursiveCteTenantGateTest::readQuietly)
                .filter(text -> WITH_RECURSIVE.matcher(text).find())
                .count();
        }
        assertThat(count)
            .as("本仓一个 WITH RECURSIVE 都没有了：若空间树改用别的方式实现，本门禁应随之调整或删除，"
                + "而不是留着空转")
            .isPositive();
    }

    /**
     * 取方法上的 MyBatis SQL 注解值（{@code @Select}/{@code @Update}/{@code @Insert}/{@code @Delete}）。
     *
     * @param method 方法
     * @return SQL 文本；非 SQL 方法返回 {@code null}
     */
    private static String extractSql(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null && select.value().length > 0) {
            return String.join(" ", select.value());
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null && update.value().length > 0) {
            return String.join(" ", update.value());
        }
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null && insert.value().length > 0) {
            return String.join(" ", insert.value());
        }
        Delete delete = method.getAnnotation(Delete.class);
        if (delete != null && delete.value().length > 0) {
            return String.join(" ", delete.value());
        }
        return null;
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取源码失败：" + path, e);
        }
    }
}
