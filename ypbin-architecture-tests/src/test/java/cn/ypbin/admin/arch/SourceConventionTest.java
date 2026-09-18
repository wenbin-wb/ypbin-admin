/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * admin 仓「源码级」架构约束：把 coding 铁律变成构建失败。
 *
 * <p>为什么需要本模块：全仓唯一那套架构测试在<b>另一个仓库</b>（{@code ypbin-starter-architecture-tests}，
 * 它只 {@code importPackages("cn.ypbin.starter")}），扫不到 {@code cn.ypbin.admin}——
 * admin 的「禁内联全限定类名、禁 {@code Collections.emptyXxx}、实体/DTO 禁 {@code @Data}、
 * 枚举禁 {@code ordinal()}、禁循环内 DB/RPC、实体继承例外清单」这些铁律此前<b>只靠人工 review</b>。</p>
 *
 * <p>设计取舍：Lombok {@code @Data} 是 SOURCE 级保留（字节码里看不到）、{@code switch(enum)} 会被 javac
 * 编译出 {@code ordinal()} 查表（字节码规则必然误报）、循环内 DB/RPC 属语句级信息（ArchUnit 的
 * {@code JavaClass} API 不提供控制流）——这三类只能做<b>源码扫描</b>，故本类与 {@link CodingRulesTest}
 * （字节码级）分工，而不是硬塞进 ArchUnit 的 fluent API。以上三条判断来自 starter 侧已踩过的坑
 * （见 {@code SourceConventionTest}/{@code CodingRulesTest} 的注释与「有效性自检」）。</p>
 *
 * <p>每条规则都配「有效性自检」：架构测试最大的风险是<b>规则写错却永远通过</b>（正则匹配不到目标、
 * 断言恒真），那比没有规则更危险——它给出虚假安全感。自检用合成样例反向验证规则真的会命中/放过。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SourceConventionTest {

    /** 应统一为 List.of/Map.of/Set.of 的 Collections 工厂调用 */
    private static final Pattern LEGACY_COLLECTION_FACTORY =
        Pattern.compile("Collections\\.(?:emptyList|emptyMap|emptySet|singletonList|singletonMap|singleton)\\(");

    /** 显式调用 Enum.ordinal() 的模式 */
    private static final Pattern ENUM_ORDINAL_CALL = Pattern.compile("\\.\\s*ordinal\\s*\\(\\s*\\)");

    /**
     * 内联全限定类名：≥2 段小写包名 + 大写开头类名。
     *
     * <p>不匹配 {@code SomeEnum.VALUE}（首段大写）与 {@code obj.method()}（含括号），避免误判。</p>
     */
    private static final Pattern INLINE_FQCN =
        Pattern.compile("\\b((?:[a-z][\\w$]*\\.){2,}[A-Z][\\w$]*(?:\\.[A-Z][\\w$]*)*)");

    /** Lombok @Data（行首） */
    private static final Pattern LOMBOK_DATA = Pattern.compile("^@Data\\b", Pattern.MULTILINE);

    /** 配置绑定类（@Data 的唯一合法落点，与 starter 口径一致） */
    private static final Pattern CONFIGURATION_PROPERTIES = Pattern.compile("@ConfigurationProperties\\b");

    /** {@code @CacheEvict(keys = {...})} 注解块（捕获 keys 数组原文，含 SpEL）。 */
    private static final Pattern CACHE_EVICT_BLOCK =
        Pattern.compile("@CacheEvict\\s*\\(\\s*keys\\s*=\\s*\\{([^}]*)\\}", Pattern.DOTALL);

    /** SpEL 中的单引号字面量（即缓存 key 前缀）。 */
    private static final Pattern SPEL_LITERAL = Pattern.compile("'([^']*)'");

    /** 缓存 key 常量声明（{@code private static final String X = "sys:...";}）。 */
    private static final Pattern CACHE_KEY_CONSTANT =
        Pattern.compile("static final String \\w+\\s*=\\s*\"([^\"]+)\";");

    /** 缓存 key 的唯一定义处：所有失效注解都必须与它对齐。 */
    private static final String CACHE_KEY_SOURCE =
        "ypbin-service-api/ypbin-system-api/src/main/java/cn/ypbin/admin/system/api/cache/SysCache.java";

    /** 类声明（取 extends 子句，用于实体继承体系判定） */
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
        "(?m)^\\s*(?:public\\s+|abstract\\s+|final\\s+)*class\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*"
            + "(?:extends\\s+([\\w.]+))?");

    /** 循环头：for / while / do / .forEach( */
    private static final Pattern LOOP_HEADER =
        Pattern.compile("\\b(?:for|while)\\s*\\(|\\.\\s*forEach\\s*\\(|\\bdo\\s*\\{");

    /** 方法调用（接收者 + 方法名） */
    private static final Pattern METHOD_CALL =
        Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\.\\s*([A-Za-z_$][\\w$]*)\\s*\\(");

    /**
     * 循环体内禁止出现的接收者（DB 与 RPC）。
     *
     * <p>覆盖三类：MyBatis Mapper/Dao/Repository、JDBC 直连、Feign/HTTP 客户端。
     * 刻意<b>不含</b>缓存（{@code SysCache} 等）与内存集合——它们是本地/Redis 访问，
     * 不在「循环内 DB 查询或外部 RPC」这条铁律的射程内（属已知边界，见类注释与报告）。</p>
     */
    private static final Pattern DB_OR_RPC_RECEIVER = Pattern.compile(
        "(?:Mapper|Dao|DAO|Repository|Client|Feign|FeignClient|RestTemplate|RestClient|HttpClient"
            + "|WebClient|JdbcTemplate|JdbcClient|SqlSession|NamedParameterJdbcTemplate)$");

    /** 接收者的裸名（字段就叫 {@code mapper}/{@code client} 的写法也要拦） */
    private static final Pattern BARE_DB_OR_RPC_RECEIVER =
        Pattern.compile("(?i)^(mapper|dao|client|repository|jdbctemplate|sqlsession)$");

    /**
     * 实体不继承 {@code BaseEntity}/{@code TenantBaseEntity} 的<b>显式例外清单</b>（键=类简单名，值=理由）。
     *
     * <p>两类合法例外，均须逐条写明理由（禁止「先放行再解释」）：</p>
     * <ol>
     *   <li><b>异步线程写入的追加大流量表</b>：写入无 Sa-Token 上下文，走基类审计字段自动填充会抛
     *       {@code SaTokenContextException}（本仓 {@code SysLog}/{@code SysTrackEvent} 的类注释已写明）；</li>
     *   <li><b>纯关联表</b>：只承载两个外键，无审计/逻辑删除语义（{@code ypbin-admin-dev} SKILL 第 262 行）。</li>
     * </ol>
     *
     * <p>清单本身受「过期检测」约束：每个键必须对应真实存在、且<b>确实没有</b>继承基类的实体类，
     * 否则测试失败——防止有人修好继承关系后清单静默残留（starter 侧的同源做法）。</p>
     */
    private static final Map<String, String> ENTITY_BASE_EXEMPTIONS = buildEntityBaseExemptions();

    /**
     * 循环内 DB/RPC 的<b>显式豁免清单</b>（键 {@code 类简单名#接收者.方法名}，值=理由）。
     *
     * <p>只登记「一次调用写/读一批」的合法形态：把大列表切块后<b>逐块批量</b>调用，单条 SQL 的参数
     * 个数有上限，这是本规则期望的写法而不是违规。逐行往返的单条调用不在此列，必须改批量。</p>
     *
     * <p>豁免受<b>消耗检测</b>约束：每个键都必须真的命中一次循环内调用，否则测试失败——
     * 防止有人修好代码后豁免条目静默残留、也防止有人把整类文件丢进豁免里「一键变绿」。</p>
     */
    private static final Map<String, String> LOOP_DB_EXEMPTIONS = Map.of(
        "NoticePublishServiceImpl#deliveryMapper.insertBatch",
        "分块批量插入：按 INSERT_BATCH_SIZE 切块后每块一次 insertBatch（见 insertInBatches），非逐行往返",
        "AiDocumentVectorizer#chunkMapper.insertBatch",
        "分块批量插入：分块数由文档大小决定（不可控），按 INSERT_BATCH_SIZE 分块后每块一次 insertBatch，"
            + "规避 max_allowed_packet，非逐行往返",
        "UserExcelComponent#userMapper.insertBatch",
        "分块批量插入：导入行数由上传文件决定（不可控），按 INSERT_BATCH_SIZE 分块后每块一次 insertBatch，"
            + "规避 max_allowed_packet，非逐行往返",
        "AiModelConfigServiceImpl#client.send",
        "误报：候选补全地址回退尝试（for 遍历 completionUrls，命中首个非 404 即 break），"
            + "循环次数与数据量无关，不存在 N+1；规则只看「循环体里有没有 RPC 接收者」，识别不了 break 语义");

    /**
     * 构建实体继承例外清单。
     *
     * @return 类简单名 → 理由
     */
    private static Map<String, String> buildEntityBaseExemptions() {
        Map<String, String> exemptions = new LinkedHashMap<>();
        // ① 异步/消费者线程写入，取不到 Sa-Token 上下文
        exemptions.put("SysLog", "操作/登录日志由 @Async 线程落库，异步线程无 sa-token 上下文（类注释已写明）");
        exemptions.put("SysTrackEvent", "埋点明细由采集链路消费者线程写入，无 sa-token 上下文（类注释已写明）");
        exemptions.put("SysTrackSession", "埋点会话由聚合任务线程写入，无 sa-token 上下文（类注释已写明）");
        exemptions.put("SysTrackEventDaily", "埋点按天聚合由聚合任务线程写入，同 SysTrackSession");
        exemptions.put("SysTrackUserDaily", "埋点按天聚合由聚合任务线程写入，同 SysTrackSession");
        // ② 纯关联表：只有外键，无审计/逻辑删除语义
        exemptions.put("SysUserRole", "纯关联表，仅承载两个外键（类注释已写明）");
        exemptions.put("SysUserPost", "纯关联表，仅承载两个外键");
        exemptions.put("SysRoleMenu", "纯关联表，仅承载两个外键（类注释已写明）");
        exemptions.put("SysRoleDept", "纯关联表，仅承载两个外键");
        exemptions.put("SysTemplateMenu", "纯关联表，仅承载两个外键");
        // ③ 成批写入的投递明细（历史既有形态，本轮门禁只登记不判定）
        exemptions.put("SysNoticeDelivery", "公告投递明细（成批写入），无审计/逻辑删除语义");
        return Map.copyOf(exemptions);
    }

    /**
     * 剥离注释与字符串/字符/文本块字面量，返回「有效代码」文本（保留换行以维持行号）。
     *
     * <p>铁律类违规只可能出现在有效代码中；注释与字符串里的 {@code cn.ypbin.*} 属合法内容
     * （如 Javadoc 引用、{@code Class.forName("...")}）。文本块必须整体跳过：否则内容里的引号会
     * 让剥离器与后续代码错位，其后的代码被整段吞掉，所有消费剥离文本的规则<b>静默失明</b>。</p>
     *
     * @param source 原始源码
     * @return 去掉注释与字面量后的代码文本
     */
    static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char ch = source.charAt(i);
            if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (ch == '"' && i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                // 文本块：整体跳过，避免奇数个引号导致后续代码被吞
                i += 3;
                while (i < n) {
                    if (source.charAt(i) == '\\') {
                        i += 2;
                        continue;
                    }
                    if (source.charAt(i) == '"' && i + 2 < n
                        && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                        i += 3;
                        break;
                    }
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
            } else if (ch == '"' || ch == '\'') {
                char quote = ch;
                i++;
                while (i < n) {
                    char current = source.charAt(i);
                    if (current == '\\') {
                        i += 2;
                        continue;
                    }
                    if (current == quote) {
                        i++;
                        break;
                    }
                    if (current == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static int lineNumber(String code, int index) {
        return (int) code.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1;
    }

    /**
     * 检测「循环体内执行 DB 调用或外部 RPC」。
     *
     * <p>这是语句级信息（ArchUnit 的类/方法 API 拿不到循环边界），故按循环头做括号/花括号配对取循环体。
     * 覆盖 {@code for} / {@code while} / {@code do} / {@code .forEach(...)}；循环体内的 lambda 同样算循环内
     * （如 {@code list.forEach(x -> mapper.insert(x))}）。</p>
     *
     * <p><b>已知边界（如实声明，避免虚假安心）</b>：</p>
     * <ul>
     *   <li>{@code stream().map(x -> mapper.select(x))} 这类<b>非 forEach 的流式中间操作</b>不被识别为循环
     *       ——本仓当前无此写法（2026-09-16 实测 0 处），但它确实是 N+1 的等价形态；</li>
     *   <li>通过 Service 层方法间接打库（{@code loop { fooService.load(id); }}）识别不到；</li>
     *   <li>接收者必须是字段/局部变量名形如 {@code *Mapper/*Dao/*Client/...} 或裸名
     *       {@code mapper/client/dao}；把 Mapper 赋给别名的写法会漏判；</li>
     *   <li>{@code do {...} while (cond);} 的尾部 {@code while} 会被当成新循环头（本仓主源码 2026-09-16
     *       实测 0 处 do-while，一旦引入需同步修本方法）。</li>
     * </ul>
     *
     * @param code 已剥离注释与字面量的代码文本
     * @return 违规点（按出现顺序去重）
     */
    static List<LoopDbCall> loopDbCallsInLoops(String code) {
        Set<LoopDbCall> hits = new LinkedHashSet<>();
        Matcher loopMatcher = LOOP_HEADER.matcher(code);
        while (loopMatcher.find()) {
            int[] span = loopBodySpan(code, loopMatcher.start(), loopMatcher.end());
            if (span == null) {
                continue;
            }
            String body = code.substring(span[0], span[1]);
            Matcher callMatcher = METHOD_CALL.matcher(body);
            while (callMatcher.find()) {
                String receiver = callMatcher.group(1);
                String method = callMatcher.group(2);
                boolean dbOrRpc = DB_OR_RPC_RECEIVER.matcher(receiver).find()
                    || BARE_DB_OR_RPC_RECEIVER.matcher(receiver).matches();
                if (dbOrRpc) {
                    hits.add(new LoopDbCall(lineNumber(code, span[0] + callMatcher.start()), receiver, method));
                }
            }
        }
        return new ArrayList<>(hits);
    }

    /** 便于自检断言的字符串视图：{@code L行号 接收者.方法()}。 */
    static List<String> loopInternalDbOrRpcCalls(String code) {
        return loopDbCallsInLoops(code).stream()
            .map(call -> "L" + call.line() + " " + call.signature() + "()")
            .toList();
    }

    /**
     * 一处循环内 DB/RPC 调用。
     *
     * @param line     行号（1 起）
     * @param receiver 接收者标识（字段或局部变量名）
     * @param method   方法名
     */
    record LoopDbCall(int line, String receiver, String method) {

        /** 豁免清单用的签名：{@code 接收者.方法名} */
        String signature() {
            return receiver + "." + method;
        }
    }

    /**
     * 求循环体区间：先定位循环头的右括号（{@code do} 无括号，取花括号），再取「花括号块」或
     * 「到分号为止的单条语句」。
     */
    private static int[] loopBodySpan(String code, int headerStart, int headerEnd) {
        String header = code.substring(headerStart, headerEnd);
        int parenStart = header.contains("(") ? code.indexOf('(', headerStart) : -1;
        if (parenStart >= 0 && header.replace(" ", "").contains(".forEach(")) {
            // .forEach(...) 的循环体是**括号内**的 lambda 表达式本身
            // （照抄 for/while 的「括号之后」逻辑会整段漏判 lambda 体，自检样例已固化该边界）
            int parenEnd = matchingParen(code, parenStart);
            return parenEnd < 0 ? null : new int[] {parenStart + 1, parenEnd - 1};
        }
        int bodyStart;
        if (parenStart >= 0) {
            int parenEnd = matchingParen(code, parenStart);
            if (parenEnd < 0) {
                return null;
            }
            bodyStart = parenEnd;
        } else {
            bodyStart = headerEnd;
        }
        int i = bodyStart;
        while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
            i++;
        }
        if (i >= code.length()) {
            return null;
        }
        if (code.charAt(i) == '{') {
            int end = matchingBrace(code, i);
            return end < 0 ? null : new int[] {i, end};
        }
        // 单条语句：到第 0 层的分号为止
        int depth = 0;
        for (int j = i; j < code.length(); j++) {
            char ch = code.charAt(j);
            if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                if (depth == 0) {
                    return new int[] {i, j};
                }
                depth--;
            } else if (ch == ';' && depth == 0) {
                return new int[] {i, j + 1};
            }
        }
        return new int[] {i, code.length()};
    }

    /** 返回右括号后一位的下标；不匹配时返回 -1 */
    private static int matchingParen(String code, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** 返回右花括号后一位的下标；不匹配时返回 -1 */
    private static int matchingBrace(String code, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /**
     * 校验：{@code @CacheEvict} 里出现的 key 字面量必须都能在 {@code SysCache} 的 key 常量里找到。
     *
     * <p><b>覆盖边界</b>：只检查 {@code @CacheEvict(keys = {...})} 里的 SpEL <b>字面量</b>；
     * 若改用常量引用（{@code keys = KEY_CONSTANT}）或改走 {@code SysCache.evictXxx()}，本规则看不到——
     * 新增这类写法时需一并扩展规则，别把它当成万能兜底。</p>
     *
     * <p><b>为什么需要这条规则</b>：缓存 key 一旦改版（例如载荷由实体收窄为视图后升 v2），
     * 若失效注解仍指向旧 key，则「失效」打在不再被读取的键上——读侧继续命中旧快照且<b>完全静默</b>
     * （典型后果：改状态/改角色后登录仍用旧快照）。本规则把它变成构建失败。</p>
     *
     * @param source       待检查的源码
     * @param declaredKeys {@code SysCache} 中声明的 key 常量集合
     * @return 未在 {@code SysCache} 中声明的 key 字面量列表
     */
    static List<String> cacheEvictKeysNotDeclared(String source, Set<String> declaredKeys) {
        List<String> violations = new ArrayList<>();
        Matcher blocks = CACHE_EVICT_BLOCK.matcher(source);
        while (blocks.find()) {
            Matcher literals = SPEL_LITERAL.matcher(blocks.group(1));
            while (literals.find()) {
                String key = literals.group(1);
                if (!declaredKeys.contains(key)) {
                    violations.add(key);
                }
            }
        }
        return violations;
    }

    // ------------------------------------------------------------------ 规则

    @Test
    @DisplayName("禁止内联全限定类名（import/package 行除外）")
    void shouldNotUseInlineFullyQualifiedClassNames() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index].trim();
                if (line.startsWith("package ") || line.startsWith("import ")) {
                    continue;
                }
                Matcher matcher = INLINE_FQCN.matcher(lines[index]);
                if (matcher.find()) {
                    violations.add(SourceScan.relative(file) + ":" + (index + 1) + " → " + matcher.group(1));
                }
            }
        }
        assertThat(violations)
            .as("存在内联全限定类名，请改为顶部 import 后使用简单类名"
                + "（唯一例外：注解属性要求编译期常量）")
            .isEmpty();
    }

    @Test
    @DisplayName("集合字面量统一用 List.of/Map.of/Set.of，禁用 Collections.emptyXxx/singletonXxx")
    void shouldUseImmutableFactoriesInsteadOfCollectionsHelpers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (LEGACY_COLLECTION_FACTORY.matcher(lines[index]).find()) {
                    violations.add(SourceScan.relative(file) + ":" + (index + 1));
                }
            }
        }
        assertThat(violations)
            .as("请改用 List.of()/Map.of()/Set.of()（语义等价且更简洁）；注意不可变工厂不接受 null 元素")
            .isEmpty();
    }

    @Test
    @DisplayName("@Data 仅允许用于 @ConfigurationProperties 配置绑定类（实体/DTO 一律 @Getter @Setter）")
    void lombokDataShouldOnlyBeUsedOnConfigurationProperties() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (LOMBOK_DATA.matcher(source).find() && !CONFIGURATION_PROPERTIES.matcher(source).find()) {
                violations.add(SourceScan.relative(file));
            }
        }
        assertThat(violations)
            .as("实体/DTO 用 @Data 会污染 equals/hashCode（含集合与关联对象）并在 toString 输出 password 等敏感字段，"
                + "请改用 @Getter @Setter")
            .isEmpty();
    }

    @Test
    @DisplayName("禁止显式调用 Enum.ordinal()（存库/传参一律用 code）")
    void shouldNotCallEnumOrdinalExplicitly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (ENUM_ORDINAL_CALL.matcher(lines[index]).find()) {
                    violations.add(SourceScan.relative(file) + ":" + (index + 1));
                }
            }
        }
        assertThat(violations)
            .as("枚举必须显式声明 code/desc，存库与传参一律用 code；ordinal 会因枚举顺序调整而错乱")
            .isEmpty();
    }

    @Test
    @DisplayName("禁止在循环内执行 DB 调用或外部 RPC（N+1 零容忍）")
    void loopsMustNotCallDbOrRpc() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<String> consumedExemptions = new LinkedHashSet<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String className = file.getFileName().toString().replace(".java", "");
            for (LoopDbCall call : loopDbCallsInLoops(code)) {
                String exemptionKey = className + "#" + call.signature();
                if (LOOP_DB_EXEMPTIONS.containsKey(exemptionKey)) {
                    consumedExemptions.add(exemptionKey);
                    continue;
                }
                violations.add(SourceScan.relative(file) + ":" + call.line() + " → " + call.signature() + "()");
            }
        }
        assertThat(violations)
            .as("循环体内不得执行 DB 查询/写入或外部 RPC（N+1）：请抽出批量 IN 查询或批量写；"
                + "批量 IN 前必须先判空短路返回空集合。确属「分块批量写入」合法形态的，"
                + "请加进 LOOP_DB_EXEMPTIONS 并写明理由（禁止直接放宽规则）")
            .isEmpty();
        assertThat(consumedExemptions)
            .as("LOOP_DB_EXEMPTIONS 存在未被命中的失效条目：代码已修好或已删除，必须同步删除豁免"
                + "（否则门禁会长期放行同类违规）")
            .containsExactlyInAnyOrderElementsOf(LOOP_DB_EXEMPTIONS.keySet());
    }

    @Test
    @DisplayName("实体继承体系：不继承 BaseEntity/TenantBaseEntity 的例外清单必须显式且无残留")
    void entitiesMustExtendBaseEntityUnlessExplicitlyExempted() throws IOException {
        Set<String> baseTypes = Set.of("BaseEntity", "TenantBaseEntity");
        List<String> discovered = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            if (!code.contains("@TableName(")) {
                continue;
            }
            Matcher declaration = CLASS_DECLARATION.matcher(code);
            if (!declaration.find()) {
                violations.add(SourceScan.relative(file) + " → 命中 @TableName 但未能解析类声明（规则需同步更新）");
                continue;
            }
            String simpleName = declaration.group(1);
            String superType = declaration.group(2);
            String superSimpleName = superType == null ? null
                : superType.substring(superType.lastIndexOf('.') + 1);
            if (superSimpleName != null && baseTypes.contains(superSimpleName)) {
                continue;
            }
            discovered.add(simpleName);
            if (!ENTITY_BASE_EXEMPTIONS.containsKey(simpleName)) {
                violations.add(SourceScan.relative(file) + " → 实体 " + simpleName
                    + " 既未继承 BaseEntity/TenantBaseEntity，也不在显式例外清单中");
            }
        }
        assertThat(violations)
            .as("实体继承体系的例外必须逐条写明理由（异步线程写入的日志/埋点表、纯关联表）；"
                + "未登记者请补继承或在 ENTITY_BASE_EXEMPTIONS 里显式登记")
            .isEmpty();

        // 过期检测：清单里的每一项都必须确实存在、且确实没有继承基类，否则清单在骗人
        List<String> stale = new ArrayList<>();
        for (String exempted : ENTITY_BASE_EXEMPTIONS.keySet()) {
            if (!discovered.contains(exempted)) {
                stale.add(exempted + "（已不在「未继承基类」的实体集合中：要么类被删/改名，要么已改回继承基类）");
            }
        }
        assertThat(stale)
            .as("例外清单存在失效条目，必须删除或更正（否则门禁会长期放行不该放行的实体）")
            .isEmpty();
        assertThat(ENTITY_BASE_EXEMPTIONS)
            .as("例外清单必须逐条写明理由")
            .allSatisfy((name, reason) -> assertThat(reason).isNotBlank());
    }

    // ------------------------------------------------------------ 有效性自检

    @Test
    @DisplayName("内联 FQCN 检测正则应命中违规、放过合法写法（规则有效性自检）")
    void inlineFqcnPatternShouldBeAccurate() {
        assertThat(INLINE_FQCN.matcher("List<AiDocumentVO> results = new java.util.ArrayList<>();").find())
            .isTrue();
        assertThat(INLINE_FQCN.matcher("} catch (cn.dev33.satoken.exception.NotLoginException e) {").find())
            .isTrue();
        // 合法：简单类名、枚举常量、方法调用
        assertThat(INLINE_FQCN.matcher("private final SysUserService userService;").find()).isFalse();
        assertThat(INLINE_FQCN.matcher("return UserStatusEnum.ENABLED.getCode();").find()).isFalse();
        assertThat(INLINE_FQCN.matcher("sysUser.getUsername()").find()).isFalse();
    }

    @Test
    @DisplayName("集合工厂检测正则应能命中违规（规则有效性自检）")
    void legacyFactoryPatternShouldCatchViolation() {
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("return Collections.emptyList();").find()).isTrue();
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("Collections.singletonList(key)").find()).isTrue();
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("Collections.unmodifiableList(list)").find()).isFalse();
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("List.of()").find()).isFalse();
    }

    @Test
    @DisplayName("ordinal 检测正则应能命中违规、且不误报 switch(enum)（规则有效性自检）")
    void ordinalPatternShouldCatchViolation() {
        assertThat(ENUM_ORDINAL_CALL.matcher("return status.ordinal();").find()).isTrue();
        assertThat(ENUM_ORDINAL_CALL.matcher("return status . ordinal ( );").find()).isTrue();
        // switch(enum) 的 ordinal 只存在于字节码，源码正则天然不命中（这正是必须做源码扫描的原因）
        assertThat(ENUM_ORDINAL_CALL.matcher("switch (status) { case ENABLED -> 1; }").find()).isFalse();
    }

    @Test
    @DisplayName("循环内 DB/RPC 检测应能命中各类循环、放过循环外调用（规则有效性自检）")
    void loopDetectionShouldBeAccurate() {
        // 命中：for 块 + Mapper
        assertThat(loopInternalDbOrRpcCalls(
            "for (Long id : ids) {\n    roleMenuMapper.insert(new SysRoleMenu(1L, id));\n}\n")).hasSize(1);
        // 命中：forEach 的 lambda 体内
        assertThat(loopInternalDbOrRpcCalls(
            "ids.forEach(id -> roleMenuMapper.insert(new SysRoleMenu(1L, id)));\n")).hasSize(1);
        // 命中：while + RPC 客户端
        assertThat(loopInternalDbOrRpcCalls(
            "while (hasNext()) {\n    systemClient.getUserById(1L);\n}\n")).hasSize(1);
        // 命中：单条语句循环（无花括号）
        assertThat(loopInternalDbOrRpcCalls(
            "for (Long id : ids)\n    roleMenuMapper.insert(new SysRoleMenu(1L, id));\n")).hasSize(1);
        // 命中：for 里的查询
        assertThat(loopInternalDbOrRpcCalls(
            "for (Long id : ids) {\n    users.add(userMapper.selectById(id));\n}\n")).hasSize(1);
        // 放过：循环体只操作内存集合
        assertThat(loopInternalDbOrRpcCalls(
            "for (Long id : ids) {\n    cacheKeys.add(id);\n}\n")).isEmpty();
        // 放过：批量调用在循环外（正确写法）
        assertThat(loopInternalDbOrRpcCalls(
            "for (Long id : ids) {\n    rows.add(toRow(id));\n}\nroleMenuMapper.insertBatch(rows);\n")).isEmpty();
        // 放过：缓存不是 DB/RPC（已知边界，见方法注释）
        assertThat(loopInternalDbOrRpcCalls(
            "users.forEach(u -> SysCache.evictUser(null, u.getUsername()));\n")).isEmpty();
        // 放过：字面量/局部变量的普通调用
        assertThat(loopInternalDbOrRpcCalls(
            "for (String s : list) {\n    sb.append(s.trim());\n}\n")).isEmpty();
    }

    @Test
    @DisplayName("实体继承例外清单应命中未登记项、不误报已登记项（规则有效性自检）")
    void entityExemptionListShouldBeAccurate() {
        // 未登记且未继承 → 应被判定为违规
        assertThat(ENTITY_BASE_EXEMPTIONS).doesNotContainKey("SysDefinitelyNotExempted");
        // 清单里的名字必须是非空的类简单名，且理由非空（防止「空理由豁免」）
        assertThat(ENTITY_BASE_EXEMPTIONS).isNotEmpty();
        assertThat(ENTITY_BASE_EXEMPTIONS.keySet()).allSatisfy(name ->
            assertThat(name).matches("^[A-Z]\\w*$"));
        // 两类合法例外的代表条目必须在清单内（防止有人下次误删）
        assertThat(ENTITY_BASE_EXEMPTIONS).containsKeys("SysLog", "SysTrackEvent", "SysRoleMenu");
    }

    @Test
    @DisplayName("循环内 DB/RPC 豁免清单格式自检（键=类#接收者.方法，理由非空）")
    void loopExemptionListShouldBeWellFormed() {
        assertThat(LOOP_DB_EXEMPTIONS).isNotEmpty();
        assertThat(LOOP_DB_EXEMPTIONS.keySet()).allSatisfy(key ->
            assertThat(key).matches("^[A-Z]\\w*#[a-zA-Z_]\\w*\\.[a-zA-Z_]\\w*$"));
        assertThat(LOOP_DB_EXEMPTIONS).allSatisfy((key, reason) -> assertThat(reason).isNotBlank());
    }

    @Test
    @DisplayName("源码剥离后大括号必须平衡（否则多条规则会静默失明）")
    void strippingShouldPreserveBraceBalance() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            long open = code.chars().filter(ch -> ch == '{').count();
            long close = code.chars().filter(ch -> ch == '}').count();
            if (open != close) {
                violations.add(SourceScan.relative(file) + " → 剥离后 { =" + open + " 而 } =" + close);
            }
        }
        assertThat(violations)
            .as("剥离器一旦吞掉代码，内联 FQCN/ordinal/循环内 DB 等规则会静默失效；"
                + "常见成因是字符串/字符/文本块字面量未被正确跳过")
            .isEmpty();
    }

    @Test
    @DisplayName("文本块剥离正误样例（规则有效性自检）")
    void textBlockStrippingShouldBeAccurate() {
        // 文本块内含奇数个引号：错误实现会从这里与后续代码错位，把 hidden 整段吞掉
        String source = "class A {\n    String s = \"\"\"\n    he said \"hi and left\n    \"\"\";\n"
            + "    void hidden() {}\n}\n";
        String stripped = stripCommentsAndLiterals(source);
        assertThat(stripped).contains("void hidden()");
        assertThat(stripped.chars().filter(ch -> ch == '{').count())
            .isEqualTo(stripped.chars().filter(ch -> ch == '}').count());
        // 注释与字符串里的循环内 mapper 调用不应误判
        assertThat(loopInternalDbOrRpcCalls(stripCommentsAndLiterals(
            "// for (Long id : ids) { mapper.selectById(id); }\nString sql = \"for (...) mapper.insert(x)\";\n")))
            .isEmpty();
    }

    @Test
    @DisplayName("@CacheEvict 的 key 必须与 SysCache 的 key 常量一致（防失效打在旧 key 上而静默不生效）")
    void cacheEvictKeysMustMatchSysCacheConstants() throws IOException {
        String cacheKeySource =
            Files.readString(SourceScan.repoRoot().resolve(CACHE_KEY_SOURCE), StandardCharsets.UTF_8);
        Set<String> declaredKeys = new LinkedHashSet<>();
        Matcher constants = CACHE_KEY_CONSTANT.matcher(cacheKeySource);
        while (constants.find()) {
            declaredKeys.add(constants.group(1));
        }
        assertThat(declaredKeys)
            .as("未能从 SysCache 解析出任何 key 常量——规则已失效，请检查 CACHE_KEY_CONSTANT 是否与源码脱节")
            .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            for (String key : cacheEvictKeysNotDeclared(source, declaredKeys)) {
                violations.add(SourceScan.relative(file) + " → " + key);
            }
        }
        assertThat(violations)
            .as("这些 @CacheEvict 的 key 在 SysCache 中不存在：失效会打在不再被读取的键上（静默失效）。"
                + "改缓存 key 时必须同步改失效注解，或把 key 收进 SysCache 常量")
            .isEmpty();
    }

    @Test
    @DisplayName("@CacheEvict key 一致性检测应命中漂移 key、不误报合法 key（规则有效性自检）")
    void cacheEvictKeyPatternShouldBeAccurate() {
        Set<String> declared = Set.of("sys:user:v2:id:", "sys:user:v2:username:");
        // 合法：与 SysCache 常量一致（单键与多键两种写法）
        assertThat(cacheEvictKeysNotDeclared(
            "@CacheEvict(keys = {\"'sys:user:v2:id:' + #id\"})", declared)).isEmpty();
        assertThat(cacheEvictKeysNotDeclared(
            "@CacheEvict(keys = {\"'sys:user:v2:id:' + #id\", \"'sys:user:v2:username:' + #req.username\"})",
            declared)).isEmpty();
        // 违规：漏改的旧 key（升版前形态）
        assertThat(cacheEvictKeysNotDeclared(
            "@CacheEvict(keys = {\"'sys:user:id:' + #id\"})", declared))
            .containsExactly("sys:user:id:");
    }
}
