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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门禁模块自身的装配与<b>扫描范围</b>校验：本模块必须「跑得到、只扫本仓、又不被发布」。
 *
 * <p>为什么要有这个测试：本仓已实际踩到三次「门禁静默失效/口径错」——
 * ① 非发布模块若写进顶层 {@code <modules>} 会混进发布产物；
 * ② 门禁模块若漏出 {@code dev-only} profile 就不会执行（「没报错」其实是「没检查」）；
 * ③ <b>扫描范围若从「工作区根」出发，CI 把 starter 仓库检出到工作区内部时会把 starter 一起扫进来</b>
 * ——本机全绿、CI 全红（2026-09-16 实测），本类最后一条用例就是为这个场景立的回归门禁。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class ModulePublishingTest {

    private static final String GATE_MODULE = SourceScan.GATE_MODULE;

    @Test
    @DisplayName("清单解析自检：顶层 modules 与 dev-only profile 应解析出预期内容")
    void moduleListParsingShouldBeAccurate() throws IOException {
        List<String> top = SourceScan.topLevelModules();
        List<String> devOnly = SourceScan.profileModules("dev-only");

        assertThat(top).as("顶层 modules 解析失败或为空").contains("ypbin-common", "ypbin-auth");
        assertThat(devOnly)
            .as("dev-only profile 未解析出模块：架构门禁会静默不执行（这正是 starter 侧踩过的坑）")
            .contains(GATE_MODULE);
        assertThat(top)
            .as("非发布模块不得出现在顶层 modules（否则会混进发布/部署产物）")
            .doesNotContain(GATE_MODULE);
        assertThat(SourceScan.moduleRoots())
            .as("模块目录必须真的解析出来，否则扫描范围为空 = 所有源码规则静默通过")
            .isNotEmpty();
    }

    @Test
    @DisplayName("扫描范围：只含本仓模块源码，且不含任何嵌套检出的第三方仓库")
    void scanScopeShouldCoverOwnModulesOnly() throws IOException {
        List<String> sources = SourceScan.mainSources().stream().map(SourceScan::relative).toList();

        assertThat(sources).as("本仓模块源码必须被扫到，否则规则是空转").isNotEmpty();
        assertThat(sources).as("正向对照：ypbin-common 的主源码必须在范围内")
            .anyMatch(path -> path.startsWith("ypbin-common/src/main/java/"));
        assertThat(sources).as("扫描范围不得包含嵌套检出的第三方仓库")
            .noneMatch(path -> path.startsWith("ypbin-starter") || path.contains("/ypbin-starter/"));
    }

    @Test
    @DisplayName("门禁模块必须声明 maven.deploy.skip 且不产生主源码产物")
    void gateModuleMustNotBePublished() throws IOException {
        Path modulePom = SourceScan.repoRoot().resolve(GATE_MODULE).resolve("pom.xml");
        assertThat(Files.exists(modulePom)).as("门禁模块 pom 不存在").isTrue();
        assertThat(Files.readString(modulePom, StandardCharsets.UTF_8))
            .as("纯测试模块必须 maven.deploy.skip=true")
            .contains("<maven.deploy.skip>true</maven.deploy.skip>");
        assertThat(Files.exists(SourceScan.repoRoot().resolve(GATE_MODULE).resolve("src/main")))
            .as("门禁模块不应有主源码（有任何 src/main 都意味着它开始产出可被依赖的产物）")
            .isFalse();
    }

    @Test
    @DisplayName("CI 必须以「未激活显式 profile」的方式跑到门禁（否则 dev-only 失效、门禁空转）")
    void ciMustRunTheGateWithoutExplicitProfile() throws IOException {
        Path ci = SourceScan.repoRoot().resolve(".github/workflows/ci.yml");
        assertThat(Files.exists(ci)).as("未找到 CI 工作流").isTrue();
        List<String> gateRuns = new ArrayList<>();
        for (String line : Files.readString(ci, StandardCharsets.UTF_8).split("\n")) {
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
    void architectureRulesShouldLiveOnlyHere() {
        assertThat(archRuleFilesOutsideGateModule())
            .as("ArchUnit 规则只应集中在 " + GATE_MODULE + "；散落会造成规则口径漂移")
            .isEmpty();
    }

    @Test
    @DisplayName("CI 场景模拟：工作区根/模块内嵌套检出第三方仓库时，规则仍只扫本仓源码")
    void nestedThirdPartyCheckoutMustNotBeScanned() throws IOException {
        Path repoRoot = SourceScan.repoRoot();

        // 场景 A：CI 的真实形态——starter 被检出到工作区根（<repoRoot>/ypbin-starter）
        Path nestedAtRoot = repoRoot.resolve("ypbin-starter");
        // 标记文件：只删「本测试造的」目录。若上一次运行被 kill 留下残留（git 不显示空目录，
        // 下一次运行的 rootCreatedByTest 会是 false 从而永不清理），这里先自愈清掉。
        Path marker = nestedAtRoot.resolve(MARKER_FILE);
        if (Files.exists(marker)) {
            deleteRecursively(nestedAtRoot);
        }
        boolean rootCreatedByTest = !Files.exists(nestedAtRoot);
        Path fakeArchRule = nestedAtRoot.resolve(
            "ypbin-starter-architecture-tests/src/test/java/cn/ypbin/starter/arch/FakeArchRuleTest.java");
        Path fakeLoop = nestedAtRoot.resolve(
            "ypbin-starter-tracking/src/main/java/cn/ypbin/starter/tracking/FakeLoopViolation.java");
        // 场景 B：嵌套在某个本仓模块目录内（走「按目录名剪枝」这条路径）
        Path nestedInModule = repoRoot.resolve("ypbin-common").resolve("ypbin-starter");
        Path fakeInModule = nestedInModule.resolve("src/main/java/x/FakeInnerLoopViolation.java");
        // 场景 C：目录名不认识、但带 .git（说明它是一个独立仓库的工作区）
        Path foreignRepo = repoRoot.resolve("ypbin-common").resolve("some-other-checkout");
        Path fakeInForeign = foreignRepo.resolve("src/main/java/x/FakeForeignLoopViolation.java");

        try {
            write(marker, "由 ypbin-architecture-tests 的 CI 场景模拟用例创建，可安全删除\n");
            write(fakeArchRule, "package cn.ypbin.starter.arch;\n"
                + "import com.tngtech.archunit.core.importer.ClassFileImporter;\n"
                + "class FakeArchRuleTest {\n    ClassFileImporter importer;\n}\n");
            write(fakeLoop, loopViolationSource("cn.ypbin.starter.tracking"));
            write(fakeInModule, loopViolationSource("x"));
            Files.createDirectories(foreignRepo.resolve(".git"));
            write(fakeInForeign, loopViolationSource("x"));

            // 非空转证明①：假文件的内容确实是违规——规则本身会命中它
            String fakeCode = SourceConventionTest.stripCommentsAndLiterals(
                Files.readString(fakeLoop, StandardCharsets.UTF_8));
            assertThat(SourceConventionTest.loopInternalDbOrRpcCalls(fakeCode))
                .as("自检非空转：假文件的循环体内确实调了 mapper，若被扫到规则必然报违规")
                .isNotEmpty();

            // 非空转证明②：旧的「扫工作区根」口径确实会命中它们（这正是 CI 变红的原因）
            List<String> unscoped = unscopedJavaFiles();
            assertThat(unscoped).as("假文件必须真的存在于工作区内，否则本自检证明不了任何事")
                .anyMatch(path -> path.contains("ypbin-starter/"));
            assertThat(unscoped).as("场景 C 的假文件也必须存在")
                .anyMatch(path -> path.contains("some-other-checkout/"));

            // 断言：收敛后的扫描范围把它们全部排除
            List<String> scanned = SourceScan.mainSources().stream().map(SourceScan::relative).toList();
            assertThat(scanned).as("场景 A：工作区根的 starter 检出不在任何本仓模块内，不得被扫")
                .noneMatch(path -> path.contains("ypbin-starter/"));
            assertThat(scanned).as("场景 B：模块内嵌套的 starter 必须被剪掉")
                .noneMatch(path -> path.contains("ypbin-common/ypbin-starter/"));
            assertThat(scanned).as("场景 C：带 .git 的陌生检出目录必须被剪掉")
                .noneMatch(path -> path.contains("some-other-checkout/"));
            assertThat(SourceScan.prunedPaths())
                .as("剪枝必须真的发生（记录到 prunedPaths），否则这条保护可能已静默失效")
                .anyMatch(path -> path.startsWith("ypbin-common/ypbin-starter"))
                .anyMatch(path -> path.startsWith("ypbin-common/some-other-checkout"));
            // 规则口径复算：ArchUnit 规则集合也不得再被嵌套仓里的假规则文件污染
            assertThat(archRuleFilesOutsideGateModule())
                .noneMatch(path -> path.contains("ypbin-starter/"));
        } finally {
            // 是否「本测试拥有」根级嵌套目录：必须在删标记文件**之前**判定
            boolean ownsNestedRoot = Files.exists(marker);
            for (Path created : List.of(fakeArchRule, fakeLoop, fakeInModule, fakeInForeign, marker)) {
                deleteFile(created);
                pruneEmptyParents(created, repoRoot);
            }
            // 我造的 .git 标记目录要先删掉，否则它会让上层目录「非空」而收不掉（复核发现的残留根因）
            deleteDirectoryIfEmpty(foreignRepo.resolve(".git"));
            pruneEmptyParents(fakeInForeign, repoRoot);
            if (ownsNestedRoot) {
                // 只删带标记的目录：真实 starter 检出没有该标记，绝不会被误删
                deleteRecursively(nestedAtRoot);
            }
        }
    }

    /** 只删本测试造的空目录时留下的标记文件名 */
    private static final String MARKER_FILE = ".ypbin-admin-arch-test-marker";

    /** 一段「循环内 DB 调用」的合成源码（与 {@code SourceConventionTest} 的自检样例同源） */
    private static String loopViolationSource(String packageName) {
        return "package " + packageName + ";\n"
            + "import java.util.List;\n"
            + "class FakeLoopViolation {\n"
            + "    void persist(List<Long> ids, Object roleMenuMapper) {\n"
            + "        for (Long id : ids) {\n"
            + "            roleMenuMapper.hashCode();\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    /**
     * 收集「本仓测试源码里出现 ArchUnit 用法、却不在门禁模块内」的文件。
     *
     * <p>口径与源码规则一致：只扫本仓模块（见 {@link SourceScan}），因此嵌套检出的 starter 不会误报。</p>
     *
     * @return 相对路径列表
     */
    static List<String> archRuleFilesOutsideGateModule() {
        List<String> strays = new ArrayList<>();
        try {
            for (Path path : SourceScan.testSources()) {
                String normalized = SourceScan.relative(path);
                if (normalized.startsWith(GATE_MODULE + "/")) {
                    continue;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.contains("ClassFileImporter") || content.contains("ArchRuleDefinition")) {
                    strays.add(normalized);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("扫描测试源码失败", ex);
        }
        return strays;
    }

    /**
     * 「扫整个工作区根」的旧口径（仅供自检证明假文件确实会被旧实现命中）。
     *
     * <p>注意：{@link #scanScopeShouldCoverOwnModulesOnly} 与 {@link #architectureRulesShouldLiveOnlyHere}
     * 在「工作区里本来就没有嵌套检出」的本机环境是<b>空转</b>的——它们只在 CI 那种 starter 常驻工作区
     * 的环境里才拦得住（复核意见，2026-09-16）。真正与环境无关、可变异验证的是
     * {@link #nestedThirdPartyCheckoutMustNotBeScanned}：它自己造出嵌套检出，因此本地也非空转。</p>
     */
    private static List<String> unscopedJavaFiles() throws IOException {
        Path repoRoot = SourceScan.repoRoot();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/target/"))
                .map(path -> repoRoot.relativize(path).toString().replace('\\', '/'))
                .toList();
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 清理尽力而为：残留未跟踪文件不影响断言结论，且下次运行会覆盖
        }
    }

    /**
     * 从给定文件所在目录起逐级向上删除**空目录**，直到 {@code stopAt}（不含）。
     *
     * @param created 本测试创建的文件
     * @param stopAt  停止边界（仓库根）
     */
    private static void pruneEmptyParents(Path created, Path stopAt) {
        Path dir = created.getParent();
        while (dir != null && !dir.equals(stopAt) && dir.startsWith(stopAt)) {
            if (!Files.isDirectory(dir)) {
                dir = dir.getParent();
                continue;
            }
            try (Stream<Path> children = Files.list(dir)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            } catch (IOException ex) {
                return;
            }
            try {
                Files.delete(dir);
            } catch (IOException ex) {
                return;
            }
            dir = dir.getParent();
        }
    }

    private static void deleteDirectoryIfEmpty(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> children = Files.list(dir)) {
                    if (children.findAny().isEmpty()) {
                        Files.delete(dir);
                    }
                }
            }
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(ModulePublishingTest::deleteFile);
        } catch (IOException ignored) {
            // 同上
        }
    }
}
