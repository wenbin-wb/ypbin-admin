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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门禁模块自身的装配校验：本模块必须「跑得到、又不被发布」。
 *
 * <p>为什么要有这个测试：starter 侧的教训是「约定没有门禁兜底就会静默失效」——把非发布模块写进顶层
 * {@code <modules>} 会让未签名产物混进发布包；而把门禁模块漏出 {@code dev-only} profile 又会让它
 * 在默认构建里不执行（「没报错」其实是「没检查」）。两条都靠本测试钉死。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class ModulePublishingTest {

    /** 本门禁模块的目录名 */
    private static final String GATE_MODULE = "ypbin-architecture-tests";

    private static Path repoRoot;

    private static Path rootPom;

    @BeforeAll
    static void locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve(GATE_MODULE))) {
                repoRoot = current;
                rootPom = current.resolve("pom.xml");
                return;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
            "未能定位 ypbin-admin 仓库根目录（当前目录：" + Path.of("").toAbsolutePath() + "）");
    }

    /** 解析顶层 {@code <modules>}（文件里第一个 modules 块） */
    private static List<String> topLevelModules() throws IOException {
        return modulesOf(read(rootPom), 0);
    }

    /** 解析指定 profile 下的 {@code <modules>} */
    private static List<String> profileModules(String profileId) throws IOException {
        String pom = read(rootPom);
        int profileIndex = pom.indexOf("<id>" + profileId + "</id>");
        if (profileIndex < 0) {
            return List.of();
        }
        int modulesIndex = pom.indexOf("<modules>", profileIndex);
        return modulesIndex < 0 ? List.of() : modulesOf(pom, modulesIndex);
    }

    private static List<String> modulesOf(String pom, int fromIndex) {
        int start = pom.indexOf("<modules>", fromIndex);
        if (start < 0) {
            return List.of();
        }
        int end = pom.indexOf("</modules>", start);
        if (end < 0) {
            return List.of();
        }
        List<String> modules = new ArrayList<>();
        Matcher matcher = Pattern.compile("<module>([^<]+)</module>").matcher(pom.substring(start, end));
        while (matcher.find()) {
            modules.add(matcher.group(1).trim());
        }
        return modules;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("清单解析自检：顶层 modules 与 dev-only profile 应解析出预期内容")
    void moduleListParsingShouldBeAccurate() throws IOException {
        List<String> top = topLevelModules();
        List<String> devOnly = profileModules("dev-only");

        assertThat(top).as("顶层 modules 解析失败或为空").contains("ypbin-common", "ypbin-auth");
        assertThat(devOnly)
            .as("dev-only profile 未解析出模块：架构门禁会静默不执行（这正是 starter 侧踩过的坑）")
            .contains(GATE_MODULE);
        assertThat(top)
            .as("非发布模块不得出现在顶层 modules（否则会混进发布/部署产物）")
            .doesNotContain(GATE_MODULE);
    }

    @Test
    @DisplayName("门禁模块必须声明 maven.deploy.skip 且不产生主源码产物")
    void gateModuleMustNotBePublished() throws IOException {
        Path modulePom = repoRoot.resolve(GATE_MODULE).resolve("pom.xml");
        assertThat(Files.exists(modulePom)).as("门禁模块 pom 不存在").isTrue();
        assertThat(read(modulePom))
            .as("纯测试模块必须 maven.deploy.skip=true")
            .contains("<maven.deploy.skip>true</maven.deploy.skip>");
        assertThat(Files.exists(repoRoot.resolve(GATE_MODULE).resolve("src/main")))
            .as("门禁模块不应有主源码（有任何 src/main 都意味着它开始产出可被依赖的产物）")
            .isFalse();
    }

    @Test
    @DisplayName("CI 必须以「未激活显式 profile」的方式跑到门禁（否则 dev-only 失效、门禁空转）")
    void ciMustRunTheGateWithoutExplicitProfile() throws IOException {
        Path ci = repoRoot.resolve(".github/workflows/ci.yml");
        assertThat(Files.exists(ci)).as("未找到 CI 工作流").isTrue();
        List<String> gateRuns = new ArrayList<>();
        for (String line : read(ci).split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("run:") && trimmed.contains("mvn")
                && (trimmed.contains("verify") || trimmed.contains(" test"))) {
                gateRuns.add(trimmed);
            }
        }
        assertThat(gateRuns)
            .as("CI 里找不到任何 `mvn ... verify/test` 步骤，门禁根本不会被执行")
            .isNotEmpty();
        assertThat(gateRuns.stream().filter(run -> !run.contains(" -P")).toList())
            .as("CI 的构建步骤都带上了 -P：显式 profile 会让 activeByDefault 的 dev-only 失效，"
                + "架构门禁会静默不跑（必须保留至少一个不带 -P 的 mvn verify/test 步骤）")
            .isNotEmpty();
    }

    @Test
    @DisplayName("仓库内不得存在第二套未纳管的 src/test 架构规则（避免规则漂移）")
    void architectureRulesShouldLiveOnlyHere() throws IOException {
        List<String> strays = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/target/") || normalized.contains("/" + GATE_MODULE + "/")) {
                    continue;
                }
                if (normalized.endsWith(".java") && normalized.contains("/src/test/")) {
                    String content = read(path);
                    if (content.contains("ClassFileImporter") || content.contains("ArchRuleDefinition")) {
                        strays.add(repoRoot.relativize(path).toString());
                    }
                }
            }
        }
        assertThat(strays)
            .as("ArchUnit 规则只应集中在 " + GATE_MODULE + "；散落会造成规则口径漂移")
            .isEmpty();
    }
}
