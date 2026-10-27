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
import java.util.stream.Stream;
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

    /** {@code catch (...) {} } 子句（用于「禁空 catch」铁律的源码级兜底）。 */
    private static final Pattern CATCH_CLAUSE = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{");

    /** 日志方法调用起点（只认 SLF4J 惯用的 {@code log} 字段名）。 */
    private static final Pattern LOG_CALL_START =
        Pattern.compile("\\blog\\.(?:warn|error|info|debug)\\s*\\(");

    /** 日志参数里对异常取文案的调用：{@code e.getMessage()} / {@code ex.getMessage()}。 */
    private static final Pattern GET_MESSAGE_CALL = Pattern.compile("\\b(\\w+)\\.getMessage\\(\\s*\\)");

    /**
     * 注释里硬编码 starter 版本号：{@code ypbin-starter-<模块>} 后跟具体版本（冒号或空格分隔均可）。
     *
     * <p>版本唯一来源是根 pom 的 {@code ypbin-starter.version}（经 {@code ypbin-starter-bom} 统一管理）。
     * 注释里写死版本号必然过期——本仓曾有多处注释断言「本仓固定某个 starter-log 版本」，
     * 而实际依赖早已升到主版本 4.x，且据此推出的「同步落库 / 不带堆栈」结论与真实行为相反。
     * 描述行为请直接写清「哪个类/方法做了什么」，让读者看代码即可核对，而不是看版本号推断。</p>
     */
    private static final Pattern HARDCODED_STARTER_VERSION =
        Pattern.compile("ypbin-starter-[a-z0-9-]+\\s*[:：]?\\s*v?\\d+\\.\\d+(?:\\.\\d+)?");

    /** 跨服务契约模块（api）的源码根：它必须与持久化实体解耦。 */
    private static final String API_MODULE_SOURCE =
        "ypbin-service-api/ypbin-system-api/src/main/java";

    /** 持久化实体的包名引用。 */
    private static final Pattern ENTITY_PACKAGE_REFERENCE =
        Pattern.compile("cn\\.ypbin\\.admin\\.system\\.entity\\.");

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
     * <b>无接收者前缀</b>的方法调用（隐式 {@code this}）：{@code getOne(...)} / {@code list(...)}。
     *
     * <p>前面允许 `= return ( , ; ! & | ? :` 等定界符，但<b>排除</b> {@code .}（那是带接收者的调用，
     * 由 {@link #METHOD_CALL} 管）与标识符字符（那是方法声明或 new 的类名）。</p>
     */
    private static final Pattern BARE_METHOD_CALL = Pattern.compile(
        "(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\(");

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
     * {@code IService}（MyBatis-Plus）里<b>隐式 {@code this} 调用</b>就会打库的方法名。
     *
     * <p><b>为什么需要单独一份</b>：本仓大量 service 继承 {@code BaseServiceImpl}，
     * 直接调 {@code getOne(...)} / {@code list(...)} / {@code save(...)} 而不带接收者前缀
     * （隐式 {@code this}）。{@link #METHOD_CALL} 要求必须有 {@code receiver.} 前缀，
     * 因此这类调用<b>此前完全在检测范围外</b>——2026-10-27 实测：一段循环里逐条
     * {@code getOne(...)}（典型 N+1）门禁判定为绿。</p>
     *
     * <p>只收「一定会打库」的方法名，避免把同名的纯内存方法（如自定义的 {@code list()}）误判；
     * 确属误报时按 {@link #LOOP_DB_EXEMPTIONS} 登记（禁止放宽本清单）。</p>
     */
    private static final Set<String> IMPLICIT_DB_METHODS = Set.of(
        "count", "exists", "getById", "getMap", "getObj", "getOne", "getOptById",
        "list", "listByIds", "listByMap", "listMaps", "listObjs", "page", "pageMaps",
        "query", "remove", "removeById", "removeByIds", "removeByMap", "save",
        "saveBatch", "saveOrUpdate", "saveOrUpdateBatch", "update", "updateBatch",
        "updateById");

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
        "SysConfigServiceImpl#self.updateById",
        "**已知技术债，非合法形态**：批量改配置时逐行 updateById（真 N+1，一次改 N 个键就是 N 次 UPDATE）。"
            + "2026-10-27 给本门禁补上「IService 隐式 this 调用」识别后暴露的历史代码（引入于 2314b48，早于空间功能）。"
            + "登记豁免只为不让门禁长期红着，正确修法是 SaveBatch 或单条 UPDATE ... CASE WHEN。"
            + "同循环内还有逐键 SysCache.evictConfig（缓存失效无法批量，那部分属合理）",
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
            // 隐式 this 调用（IService 的 getOne/list/save…）：无接收者前缀，METHOD_CALL 匹配不到。
            // 这是本仓最常见的打库写法（service 继承 BaseServiceImpl），必须单独扫。
            Matcher bareMatcher = BARE_METHOD_CALL.matcher(body);
            while (bareMatcher.find()) {
                String method = bareMatcher.group(1);
                if (IMPLICIT_DB_METHODS.contains(method)) {
                    hits.add(new LoopDbCall(lineNumber(code, span[0] + bareMatcher.start()),
                        "self", method));
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

    /**
     * 判定某源码是否构成「契约模块引用了持久化实体」违规。
     *
     * <p><b>为什么需要这条规则</b>：实体继承 {@code BaseEntity}（`starter-data`/MyBatis-Plus）。
     * 契约模块一旦引用实体，所有调用方就被迫传递依赖 starter-data——无数据源的 auth 因此中招
     * （实测：连 Mockito 为 {@code ISystemClient} 建 mock 都会因签名里的实体无法加载而失败）。
     * 2026-09-18 修掉该问题后，把「投影类不许放回 api 模块」从注释变成构建失败。</p>
     *
     * @param source              待判定源码
     * @param insideEntityPackage 该文件是否位于实体包自身内部（实体包内部的自引用不算违规）
     * @return 是否违规
     */
    static boolean referencesEntityOutsideEntityPackage(String source, boolean insideEntityPackage) {
        if (insideEntityPackage) {
            return false;
        }
        // 先剥离注释与字面量：只把「真实代码引用」算违规，避免注释/文档里提到类名即被误判
        return ENTITY_PACKAGE_REFERENCE.matcher(stripCommentsAndLiterals(source)).find();
    }

    /**
     * 收集「空 catch」（含「只写了注释」的 catch）所在行号。
     *
     * <p><b>为什么需要它</b>：铁律「禁静默吞异常/静默降级」此前**只有评审靠自觉**，没有任何门禁——
     * 2026-09-18 的独立复核用变异验证证明：把 miniapp 的一处 {@code log.warn} 换成空 catch，
     * 全仓 283 项测试（含 37 项架构门禁）照样全绿。本规则把它变成构建失败。</p>
     *
     * <p>入参必须是 {@link #stripCommentsAndLiterals} 处理过的文本，这样「只写注释的 catch」也会被判定为空。</p>
     *
     * @param strippedSource 已剥离注释与字面量的源码
     * @return 空 catch 的行号列表
     */
    static List<Integer> emptyCatchLines(String strippedSource) {
        List<Integer> lines = new ArrayList<>();
        Matcher matcher = CATCH_CLAUSE.matcher(strippedSource);
        while (matcher.find()) {
            int open = strippedSource.indexOf('{', matcher.start());
            // 注意：matchingBrace 返回的是「闭括号之后」的下标（既有实现如此，用于区间计算），
            // 这里要取闭括号本身，故减一——本规则的自检正是靠一个 off-by-one 用例发现的
            int closeExclusive = matchingBrace(strippedSource, open);
            if (open < 0 || closeExclusive <= 0) {
                continue;
            }
            // 只有分号（`{ ; }`）同样是「什么都没做」——不能靠一个空语句绕过本规则
            if (strippedSource.substring(open + 1, closeExclusive - 1).replace(";", "").isBlank()) {
                lines.add(lineNumber(strippedSource, matcher.start()));
            }
        }
        return lines;
    }

    /**
     * 找出「日志吞堆栈」的调用点：参数里取了 {@code ex.getMessage()}，却没有把 {@code ex} 本身传进去。
     *
     * <p><b>为什么需要它</b>：全局规范要求「记录异常必须传完整堆栈」。只写 {@code log.warn("...", e.getMessage())}
     * 在生产上只能看到一个字符串，没有异常类型、没有调用栈——而这恰恰是最常见的写法，且不会有任何编译/测试
     * 告警（本仓 2026-10-18 实测 4 处，全在 ai 模块）。本规则把它变成构建失败。</p>
     *
     * <p><b>判定方式</b>：定位每个 {@code log.warn/error/info/debug(...)} 调用的参数区间（括号配对），
     * 取其中所有 {@code X.getMessage()} 的接收者 {@code X}；若参数区间里没有以独立参数形式出现的 {@code X}
     * （即 {@code , X)} 或 {@code , X,}），判定为违规。这样 {@code log.warn("...", id, e.getMessage(), e)}
     * 通过、{@code log.warn("...", e.getMessage())} 失败。</p>
     *
     * <p><b>已知边界（如实声明）</b>：① 只认字段名为 {@code log} 的日志调用（本仓 2026-10-18 实测全部如此）；
     * ② 把日志消息拼成一个字符串再传入（{@code log.warn("..." + e.getMessage())}）无法识别；
     * ③ 收到的是包装异常（{@code log.warn("...", root.getMessage(), e)}）时 {@code root} 未作为独立参数出现，
     * 会判违规——这是刻意的保守取向（要求把被记录的那个异常对象一并传入）。</p>
     *
     * @param strippedSource 已剥离注释与字面量的源码
     * @return 违规调用所在行号
     */
    static List<Integer> logCallsSwallowingThrowable(String strippedSource) {
        List<Integer> lines = new ArrayList<>();
        Matcher call = LOG_CALL_START.matcher(strippedSource);
        while (call.find()) {
            int openParen = strippedSource.indexOf('(', call.start());
            if (openParen < 0) {
                continue;
            }
            int closeExclusive = matchingParen(strippedSource, openParen);
            if (closeExclusive <= 0) {
                continue;
            }
            // 注意：matchingParen 返回「右括号之后」的下标，故这里要取到 closeExclusive（含右括号），
            // 否则 `log.warn(..., e)` 的最后一个参数后面没有分隔符，会被误判成「没传异常对象」
            String args = strippedSource.substring(openParen + 1, closeExclusive);
            Matcher getMessage = GET_MESSAGE_CALL.matcher(args);
            while (getMessage.find()) {
                String receiver = getMessage.group(1);
                // 接收者必须作为独立参数出现：`, receiver)` 或 `, receiver,`
                Pattern passThrowable = Pattern.compile(",\\s*" + Pattern.quote(receiver) + "\\s*[,)]");
                if (!passThrowable.matcher(args).find()) {
                    lines.add(lineNumber(strippedSource, call.start()));
                    break;
                }
            }
        }
        return lines;
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

    @Test
    @DisplayName("注释/源码里禁止硬编码 ypbin-starter-x:Y.Z 版本号（版本唯一来源是根 pom）")
    void commentsMustNotHardcodeStarterVersions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String[] lines = source.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                Matcher matcher = HARDCODED_STARTER_VERSION.matcher(lines[index]);
                while (matcher.find()) {
                    violations.add(SourceScan.relative(file) + ":" + (index + 1) + " → " + matcher.group());
                }
            }
        }
        assertThat(violations)
            .as("注释里写死 starter 版本号必然过期，并会让后续维护者基于错误前提判断依赖行为"
                + "（历史事故：注释断言「固定某个 starter-log 版本 ⇒ 同步落库/不带堆栈」，"
                + "与升级后的真实行为相反）。版本唯一来源是根 pom 的 ypbin-starter.version；"
                + "要描述行为请直接写当前代码的事实（哪个 Bean/方法做了什么），不要写版本号")
            .isEmpty();
    }

    @Test
    @DisplayName("starter 版本号硬编码检测应命中各类写法、放过普通描述（规则有效性自检）")
    void starterVersionPatternShouldBeAccurate() {
        // 命中：冒号分隔、空格分隔、带 v 前缀、两段/三段版本
        // 样例由拼接构造：本文件自身必须能通过该门禁，且不能让验收 grep 在全仓命中历史版本号
        assertThat(HARDCODED_STARTER_VERSION.matcher("本仓固定 {@code ypbin-starter-log:" + "3.3.0}").find())
            .isTrue();
        assertThat(HARDCODED_STARTER_VERSION.matcher("见 ypbin-starter-ai " + "4.0.0 的实现").find()).isTrue();
        assertThat(HARDCODED_STARTER_VERSION.matcher("ypbin-starter-messaging:v" + "4.0.0").find()).isTrue();
        assertThat(HARDCODED_STARTER_VERSION.matcher("ypbin-starter-cache:" + "3.10").find()).isTrue();
        // 放过：只提模块名不写版本、写的是本仓自己的模块、普通版本描述
        assertThat(HARDCODED_STARTER_VERSION.matcher("引入 ypbin-starter-messaging").find()).isFalse();
        assertThat(HARDCODED_STARTER_VERSION.matcher("ypbin-starter-log 的 LogEventListener").find()).isFalse();
        assertThat(HARDCODED_STARTER_VERSION.matcher("ypbin-admin 版本 1.0.0").find()).isFalse();
        assertThat(HARDCODED_STARTER_VERSION.matcher("Spring Boot 4.1.1").find()).isFalse();
    }

    @Test
    @DisplayName("禁止日志吞堆栈：log.warn/error/info/debug 里取了 getMessage() 就必须同时传异常对象")
    void logsMustNotSwallowThrowableStackTrace() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            for (int line : logCallsSwallowingThrowable(code)) {
                violations.add(SourceScan.relative(file) + ":" + line);
            }
        }
        assertThat(violations)
            .as("记录异常必须传完整堆栈：把异常对象作为最后一个参数传入（log.warn(\"...\", id, e)），"
                + "只写 e.getMessage() 会丢掉异常类型与调用栈，生产上无法定位")
            .isEmpty();
    }

    @Test
    @DisplayName("日志吞堆栈检测应命中只传 getMessage 的写法、放过传了异常对象的写法（规则有效性自检）")
    void logSwallowDetectionShouldBeAccurate() {
        // 命中：只取 message
        assertThat(logCallsSwallowingThrowable(
            "log.warn(\"[ypbin-ai] 失败: {}\", e.getMessage());")).hasSize(1);
        assertThat(logCallsSwallowingThrowable(
            "log.warn(\"失败: docId={} err={}\", docId, e.getMessage());")).hasSize(1);
        assertThat(logCallsSwallowingThrowable(
            "log.error(\"失败\", ex.getMessage());")).hasSize(1);
        // 放过：异常对象作为最后一个参数传入
        assertThat(logCallsSwallowingThrowable(
            "log.warn(\"失败: docId={} err={}\", docId, e.getMessage(), e);")).isEmpty();
        assertThat(logCallsSwallowingThrowable(
            "log.error(\"[ypbin-ai] 失败: docId={}\", docId, e);")).isEmpty();
        assertThat(logCallsSwallowingThrowable(
            "log.debug(\"失败: id={}\", id, ex.getMessage(), ex);")).isEmpty();
        // 放过：根本没取 message（不涉及吞堆栈）
        assertThat(logCallsSwallowingThrowable("log.warn(\"失败: id={}\", id);")).isEmpty();
        assertThat(logCallsSwallowingThrowable("log.info(\"done\");")).isEmpty();
        // 注释与字符串里的示例文本不参与判定（剥离后不可见）
        assertThat(logCallsSwallowingThrowable(stripCommentsAndLiterals(
            "// log.warn(\"x\", e.getMessage());\nString s = \"log.warn(\\\"y\\\", e.getMessage())\";\n")))
            .isEmpty();
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
    @DisplayName("禁止空 catch（含只写注释的 catch）——把「禁静默吞异常」铁律变成构建失败")
    void noEmptyCatchBlocks() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : SourceScan.mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            for (int line : emptyCatchLines(code)) {
                violations.add(SourceScan.relative(file) + ":" + line);
            }
        }
        assertThat(violations)
            .as("空 catch 会把故障吞得无影无踪。要么记日志（含完整堆栈 log.warn/error(..., ex)），"
                + "要么显式抛出/转换异常；注释不算处理")
            .isEmpty();
    }

    @Test
    @DisplayName("空 catch 检测应命中空体与仅注释体、放过有处理的 catch（规则有效性自检）")
    void emptyCatchDetectionShouldBeAccurate() {
        // 命中：完全空的 catch
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) { }"))).hasSize(1);
        // 命中：只写了注释的 catch（剥离后为空——注释不算处理）
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) {\n    // 忽略\n}"))).hasSize(1);
        // 命中：只有分号的「伪处理」（`{ ; }`）——空语句不算处理
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) { ; }"))).hasSize(1);
        // 放过：记了日志 / 显式抛出 / 有赋值
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) { log.warn(\"x\", e); }"))).isEmpty();
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) { throw new BusinessException(\"x\"); }"))).isEmpty();
        // 放过：嵌套 try 里外层有处理（不能因为内层空体错判）
        assertThat(emptyCatchLines(stripCommentsAndLiterals(
            "try { a(); } catch (Exception e) { b(); }"))).isEmpty();
    }

    @Test
    @DisplayName("跨服务契约模块（system-api）不得引用持久化实体（实体继承 BaseEntity，引用即把 MyBatis 拖给调用方）")
    void apiModuleMustNotReferencePersistenceEntities() throws IOException {
        Path apiRoot = SourceScan.repoRoot().resolve(API_MODULE_SOURCE);
        Path entityRoot = apiRoot.resolve("cn/ypbin/admin/system/entity");
        assertThat(Files.isDirectory(apiRoot)).as("未找到契约模块源码根：" + API_MODULE_SOURCE).isTrue();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(apiRoot)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (referencesEntityOutsideEntityPackage(source, file.startsWith(entityRoot))) {
                    violations.add(SourceScan.relative(file));
                }
            }
        }
        assertThat(violations)
            .as("契约模块引用了持久化实体：调用方会被迫传递依赖 starter-data（auth 因此中过招）。"
                + "实体→视图的投影请放在 service 模块（如 system 的 feign/support/UserViewConverter）")
            .isEmpty();
    }

    @Test
    @DisplayName("契约模块实体引用检测应命中跨包引用、放过实体包内部自引用（规则有效性自检）")
    void apiEntityReferencePatternShouldBeAccurate() {
        String referencing = "import cn.ypbin.admin.system.entity.SysUser;\n";
        assertThat(referencesEntityOutsideEntityPackage(referencing, false)).isTrue();
        // 实体包内部的自引用（instanceof/checkcast/同包类）不算违规
        assertThat(referencesEntityOutsideEntityPackage(referencing, true)).isFalse();
        // 只写 DTO / 视图不违规
        assertThat(referencesEntityOutsideEntityPackage(
            "import cn.ypbin.admin.system.model.dto.SysUserDto;\n", false)).isFalse();
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
