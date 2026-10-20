/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 部署配置门禁：**Nacos 模板里的每个 {@code ${占位符}} 都必须有提供方**，且网关路由的
 * {@code RedirectTo} 状态码必须是 3xx。
 *
 * <p><b>为什么需要这条测试</b>（2026-10-20 dev 真实部署事故，两类）：</p>
 * <ol>
 *   <li><b>占位符无提供方</b>：{@code deploy/nacos/ypbin-system.yaml} 的
 *       {@code xxl-job.access-token} 写的是 {@code ${XXL_JOB_ACCESS_TOKEN:?...}}，但
 *       {@code docker-compose.yml} / {@code .env.example} / {@code install.sh} 三处都没有这个变量——
 *       部署时只能手工沿用运行实例上的值，配置不可复现，新环境首次部署直接启动失败。</li>
 *   <li><b>RedirectTo 非 3xx</b>：{@code ypbin-gateway.yaml} 的 {@code iot-internal-deny} 路由写成
 *       {@code RedirectTo=404,...}，而 SCG 只接受 3xx ⇒ gateway 启动即失败
 *       （{@code status must be a 3xx code, but was 404}）。</li>
 * </ol>
 *
 * <p>这两类都是「模板层缺陷，运行时才炸」，靠人眼审 yaml 很难发现（占位符长得像配置项、
 * 404 看起来更"安全"）。故钉成仓内测试：本地 {@code mvn test} 就能拦住。</p>
 *
 * @author wenbin
 * @since 2026-10-20
 */
class DeployNacosPlaceholderTest {

    /** 仓库根：从当前工作目录向上找含 {@code deploy/nacos} 的目录（不写死层级，换测试模块也不会失效）。 */
    private static final Path REPO_ROOT = locateRepoRoot();

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("deploy/nacos"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到仓库根（向上查找 deploy/nacos 失败）");
    }

    /**
     * 非注释行里的<b>环境变量式</b>占位符：{@code ${NAME}} 或 {@code ${NAME:...}}。
     *
     * <p>只认「全大写 + 下划线」的名字：Nacos 模板里还有 <b>Spring Cloud Gateway 的正则分组占位</b>
     * （如 {@code RewritePath=/iot/(?&lt;seg&gt;.*), /${seg}} 里的 {@code ${seg}}），以及 SpEL 表达式，
     * 它们由运行期解析、不来自环境变量，不能算「无提供方」。</p>
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]+)(?::[^}]*)?}");

    @Test
    @DisplayName("★ Nacos 模板里的每个 ${占位符} 都必须有提供方（install.sh 渲染 / compose 注入 / .env 定义）")
    void everyPlaceholderMustHaveProvider() throws IOException {
        Map<String, Set<String>> foundIn = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(REPO_ROOT.resolve("deploy/nacos"))) {
            for (Path yaml : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                for (String line : Files.readAllLines(yaml, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith("#")) {
                        continue;
                    }
                    Matcher matcher = PLACEHOLDER.matcher(line);
                    while (matcher.find()) {
                        foundIn.computeIfAbsent(matcher.group(1), k -> new LinkedHashSet<>())
                            .add(yaml.getFileName().toString());
                    }
                }
            }
        }
        assertThat(foundIn).as("Nacos 模板里应能解析出占位符（否则本测试失去意义）").isNotEmpty();

        Set<String> provided = placeholdersWithProvider();
        Map<String, String> missing = new LinkedHashMap<>();
        foundIn.forEach((name, where) -> {
            if (!provided.contains(name)) {
                missing.put(name, where.toString());
            }
        });

        assertThat(missing)
            .as("这些占位符在 Nacos 模板里被引用，但 install.sh 不渲染、compose 不注入、.env.example 也不定义"
                + " ⇒ 部署时无名可取（新环境首次部署会启动失败或被迫手工沿用线上值）。"
                + "要么补提供方，要么从模板里去掉该占位符")
            .isEmpty();
    }

    @Test
    @DisplayName("★ 网关 RedirectTo 的状态码必须是 3xx（SCG 只接受 3xx，写 404 会让 gateway 起不来）")
    void gatewayRedirectToMustUse3xx() throws IOException {
        Path gateway = REPO_ROOT.resolve("deploy/nacos/ypbin-gateway.yaml");
        List<String> lines = Files.readAllLines(gateway, StandardCharsets.UTF_8);

        Pattern redirect = Pattern.compile("RedirectTo=(\\d{3})\\s*,\\s*(\\S+)");
        Map<String, String> violations = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.strip().startsWith("#")) {
                continue;
            }
            Matcher matcher = redirect.matcher(line);
            while (matcher.find()) {
                int status = Integer.parseInt(matcher.group(1));
                if (status < 300 || status > 399) {
                    violations.put(matcher.group(0), "状态码 " + status + " 不是 3xx");
                }
            }
        }

        assertThat(violations)
            .as("SCG 的 RedirectTo 只接受 3xx，非 3xx 会导致 gateway 启动失败；"
                + "「拦住内网路径」的正确写法是 RedirectTo=302,<无路由的目标>（客户端跟随后仍是 404）")
            .isEmpty();
    }

    /**
     * 精确判定「哪些占位符有提供方」。
     *
     * <p><b>为什么不能用「键名出现在文件里」这种宽松判据</b>：第一版就是这么写的，变异验证时
     * 把 {@code XXL_JOB_ACCESS_TOKEN} 的三个提供方全删掉、用例<b>仍然通过</b>——因为 install.sh 的
     * <b>注释与报错文案</b>里也提到了这个键名。宽松判据等于没有判据。故这里按「真正能提供值」的
     * 三种形态精确匹配：</p>
     * <ol>
     *   <li>{@code install.sh} 里有 {@code s/${NAME}/...} 的 sed 替换（渲染进模板）；</li>
     *   <li>{@code docker-compose.yml} 里有 {@code NAME: ...} 的 environment 映射（注入容器环境）；</li>
     *   <li>{@code .env.example} 里有 {@code NAME=} 的定义行（source .env 后进环境）。</li>
     * </ol>
     *
     * @return 有提供方的占位符名集合
     * @throws IOException 读文件失败
     */
    private static Set<String> placeholdersWithProvider() throws IOException {
        Set<String> provided = new LinkedHashSet<>();

        String installSh = readIfExists("deploy/install.sh");
        Matcher render = Pattern.compile("s/\\\\?\\$\\{([A-Z][A-Z0-9_]+)}/").matcher(installSh);
        while (render.find()) {
            provided.add(render.group(1));
        }

        String compose = readIfExists("deploy/docker-compose.yml");
        Matcher envMapping = Pattern.compile("(?m)^\\s+([A-Z][A-Z0-9_]+):\\s").matcher(compose);
        while (envMapping.find()) {
            provided.add(envMapping.group(1));
        }

        String envExample = readIfExists("deploy/.env.example");
        Matcher envDef = Pattern.compile("(?m)^([A-Z][A-Z0-9_]+)=").matcher(envExample);
        while (envDef.find()) {
            provided.add(envDef.group(1));
        }
        return provided;
    }

    /**
     * 读取提供方文件全文；不存在时返回空串（缺失本身由 {@link #everyPlaceholderMustHaveProvider}
     * 的失败信息体现，不必在这里抛错）。
     *
     * @param relative 相对仓库根的路径
     * @return 文件内容或空串
     * @throws IOException 读失败
     */
    private static String readIfExists(String relative) throws IOException {
        Path path = REPO_ROOT.resolve(relative);
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }
}
