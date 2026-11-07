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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门禁：{@link SysSpaceMapper} 里的**标量子查询**必须显式带租户条件。
 *
 * <h2>为什么需要这条门禁</h2>
 *
 * 租户插件（`TenantLineInnerInterceptor`）能给**主表**追加 {@code tenant_id}，
 * 但它是否会把条件注入到**标量子查询内部**，取决于 JSqlParser 对该子查询的解析深度
 * ——这属于框架实现细节，不能作为安全保证的前提。
 *
 * <p>一旦子查询没被注入：`childCount` / `relationCount` / `hasChildren` 会跨租户统计，
 * 表现为「A 租户看到 B 租户同 pid 的子空间数量」「懒加载树永远显示展开箭头却展开为空」。
 * 这是**跨租户数据泄露**，必须靠 SQL 自身兜住，而不是靠「插件应该会处理」。</p>
 *
 * <p>与 {@code RecursiveCteTenantGateTest}（管 CTE 语句）互补：那个管 CTE 整体跳过插件的情况，
 * 本门禁管「主表被插件注入、但子查询被漏掉」这种更隐蔽的形态。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class SpaceSubqueryTenantGateTest {

    private static final Path MAPPER = Path.of(
        "src/main/java/cn/ypbin/admin/system/mapper/SysSpaceMapper.java");

    /** 匹配「对 sys_space / sys_space_relation 的标量子查询起点」 */
    private static final Pattern SUBQUERY_START = Pattern.compile(
        "\\(SELECT\\s+COUNT\\(\\*\\)\\s+FROM\\s+(sys_space|sys_space_relation)\\s+\\w+",
        Pattern.CASE_INSENSITIVE);

    /** 子查询内必须出现的租户条件 */
    private static final Pattern TENANT_CONDITION = Pattern.compile(
        "tenant_id\\s*=\\s*s\\.tenant_id", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("★ 空间 Mapper 的每个标量子查询都必须显式带 tenant_id（防跨租户统计）")
    void everyScalarSubqueryMustCarryTenantCondition() throws IOException {
        String source = Files.readString(MAPPER, StandardCharsets.UTF_8);
        // 只检查 SQL 文本（注释里会出现关键词，先去注释避免误判）
        String code = source.replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("//[^\\n]*", "");

        List<String> violations = new ArrayList<>();
        Matcher matcher = SUBQUERY_START.matcher(code);
        while (matcher.find()) {
            // 取该子查询的括号区间（到匹配的右括号）
            int open = matcher.start();
            int close = matchingParen(code, open);
            if (close < 0) {
                continue;
            }
            String subquery = code.substring(open, close + 1);
            if (!TENANT_CONDITION.matcher(subquery).find()) {
                violations.add("第 " + lineNumber(code, open) + " 行：" + subquery.replaceAll("\\s+", " "));
            }
        }

        assertThat(violations)
            .as("标量子查询缺少 tenant_id 条件 ⇒ 会跨租户统计（插件不保证注入子查询内部）")
            .isEmpty();
    }

    @Test
    @DisplayName("覆盖自检：确实扫到了标量子查询（防正则失配导致空转假绿）")
    void shouldActuallyFindSubqueries() throws IOException {
        String code = Files.readString(MAPPER, StandardCharsets.UTF_8)
            .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        Matcher matcher = SUBQUERY_START.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        // 当前有 3 处（childCount / relationCount / hasChildren）；少于此说明正则失效
        assertThat(count)
            .as("没扫到足够的标量子查询 ⇒ 本门禁空转（正则或 SQL 形态变了）")
            .isGreaterThanOrEqualTo(3);
    }

    private static int matchingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int lineNumber(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
