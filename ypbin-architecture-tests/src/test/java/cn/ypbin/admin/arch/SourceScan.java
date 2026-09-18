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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 架构规则的<b>扫描范围</b>：只扫本仓（ypbin-admin）自己的模块源码。
 *
 * <p><strong>为什么不能「扫整个工作区根」</strong>（2026-09-16 CI 实测教训）：admin 的 CI 会把
 * <em>ypbin-starter 仓库检出到 admin 工作区内部</em>（用于本地安装 starter 依赖），于是
 * 「扫工作区根」会把嵌套检出的 starter 一起扫进来——
 * ① starter 自带 {@code ypbin-starter-architecture-tests}（里面有 ArchUnit 规则），
 * 「规则只应集中在本模块」这条立刻误报；
 * ② starter 主源码里存在循环 + DB/RPC 调用，admin 的「禁循环内 DB/RPC」也误报。
 * 本机（starter 是兄弟目录、在 admin 仓之外）复跑全绿、CI 全红，根因就在扫描范围。</p>
 *
 * <p>因此扫描范围由<b>本仓聚合 pom 声明的模块</b>推导（顶层 {@code <modules>} + 各 profile 里的
 * {@code <modules>}），并在遍历时<b>显式剪掉</b>嵌套检出：目录名为已知兄弟仓
 * （{@link #NESTED_REPO_DIRS}）或目录内含 {@code .git}（说明它是一个独立仓库的工作区）。
 * 被剪掉的路径记录在 {@link #prunedPaths()} 里，供「CI 场景模拟」自检断言
 * ——避免这条保护自己变成空转。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
final class SourceScan {

    /** 本门禁模块的目录名 */
    static final String GATE_MODULE = "ypbin-architecture-tests";

    /**
     * 已知兄弟仓库的目录名（<b>本清单只为少走目录、加速遍历</b>）。
     *
     * <p><b>通用兜底判据是与名字无关的「目录内含 {@code .git}」</b>（见 {@link #sourcesIn}）——
     * 名字陌生或改名后的检出目录靠它拦住；因此本清单漏项不会造成误扫。</p>
     *
     * <p>⛔ <b>新增条目必须先在母仓目录实测存在</b>（2026-09-16 实测母仓只有
     * {@code ypbin-starter} / {@code ypbin-site} / {@code ypbin-admin-ui} / {@code ypbin-iot-starter} 四个兄弟仓）。
     * 之前这里写过 {@code ypbin-iot}、{@code ypbin-license-demo} 两个<b>并不存在</b>的名字
     * （凭记忆猜的），会误导后来者以为它们被核实过——已删除。</p>
     */
    private static final Set<String> NESTED_REPO_DIRS =
        Set.of("ypbin-starter", "ypbin-site", "ypbin-admin-ui", "ypbin-iot-starter");

    /** 遍历时直接跳过的目录名（构建产物/依赖装目录，纯性能考虑） */
    private static final Set<String> SKIPPED_DIRS = Set.of("target", "node_modules", ".git", ".flattened");

    /** 判定「这个模块真的产出主源码」的目录标记 */
    private static final String MAIN_SOURCE_MARKER = "src/main/java";

    /** 主源码的编译输出目录名（相对模块目录） */
    static final String CLASSES_DIR = "target/classes";

    private static final Pattern MODULE_TAG = Pattern.compile("<module>([^<]+)</module>");

    private static final Set<String> PRUNED = new LinkedHashSet<>();

    private static Path repoRoot;

    private SourceScan() {
    }

    /**
     * 仓库根目录（向上定位含聚合 pom 且含本模块的目录）。
     *
     * @return 仓库根
     */
    static synchronized Path repoRoot() {
        if (repoRoot != null) {
            return repoRoot;
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve(GATE_MODULE))) {
                repoRoot = current;
                return repoRoot;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
            "未能定位 ypbin-admin 仓库根目录（当前目录：" + Path.of("").toAbsolutePath() + "）");
    }

    /** 根聚合 pom 的文本 */
    static String rootPom() throws IOException {
        return Files.readString(repoRoot().resolve("pom.xml"));
    }

    /** 顶层 {@code <modules>}（文件里第一个 modules 块） */
    static List<String> topLevelModules() throws IOException {
        return modulesFrom(rootPom(), 0);
    }

    /** 指定 profile 下的 {@code <modules>} */
    static List<String> profileModules(String profileId) throws IOException {
        String pom = rootPom();
        int profileIndex = pom.indexOf("<id>" + profileId + "</id>");
        if (profileIndex < 0) {
            return List.of();
        }
        int modulesIndex = pom.indexOf("<modules>", profileIndex);
        return modulesIndex < 0 ? List.of() : modulesFrom(pom, modulesIndex);
    }

    /** 解析某位置之后的 {@code <modules>} 块 */
    static List<String> modulesFrom(String pom, int fromIndex) {
        int start = pom.indexOf("<modules>", fromIndex);
        if (start < 0) {
            return List.of();
        }
        int end = pom.indexOf("</modules>", start);
        if (end < 0) {
            return List.of();
        }
        List<String> modules = new ArrayList<>();
        Matcher matcher = MODULE_TAG.matcher(pom.substring(start, end));
        while (matcher.find()) {
            modules.add(matcher.group(1).trim());
        }
        return modules;
    }

    /**
     * 本仓全部模块目录：取聚合 pom 里<b>所有</b> {@code <modules>} 块（顶层 + 每个 profile）。
     *
     * <p>刻意不写死 profile 名：只读「第一个 modules + 某个固定 profile」的话，将来新增 profile
     * 声明源码模块时，那些模块会<b>静默逃出全部源码规则</b>（复核意见，2026-09-16）。</p>
     *
     * @return 模块目录（去重、存在性过滤）
     * @throws IOException 读 pom 失败
     */
    static List<Path> moduleRoots() throws IOException {
        Set<String> names = new LinkedHashSet<>(allModulesIn(rootPom()));
        List<Path> roots = new ArrayList<>();
        for (String name : names) {
            Path candidate = repoRoot().resolve(name);
            if (Files.isDirectory(candidate)) {
                roots.add(candidate);
            }
        }
        return roots;
    }

    /**
     * 本仓「有主源码的模块」目录：从聚合 pom 的模块清单（顶层 + 全部 profile）递归下钻
     * {@code <modules>}，收集所有含 {@code src/main/java} 的模块（叶子模块，以及自带主源码的聚合模块——
     * 口径与 {@link #mainSources()} 一致，避免「源码规则扫得到、字节码规则扫不到」的分叉）。
     *
     * <p><strong>存在的理由（2026-09-18 CI 事故）</strong>：本仓 4 个模块（{@code ypbin-system} /
     * {@code ypbin-ai} / {@code ypbin-gateway} / {@code ypbin-auth}）的
     * {@code spring-boot-maven-plugin:repackage} 没有配 classifier，主构件会被替换成
     * {@code BOOT-INF/classes} 布局的 fat jar；一旦 Maven 把该 jar 放进测试类路径，
     * {@code importPackages("cn.ypbin.admin")} <b>看不到这些模块的任何业务类</b>（jar 根下没有
     * {@code cn/ypbin/admin} 条目），而看到什么又取决于构建停在 {@code test} 还是
     * {@code package}/{@code verify}——门禁结果于是依赖打包形态。故架构测试改为
     * <b>按本方法推导出的模块目录显式导入 {@code target/classes}</b>，不再问 Maven 要的是 jar 还是目录。</p>
     *
     * <p>递归读 pom 而非写死清单：新增模块（含聚合子模块）自动进入导入范围，不会静默逃出门禁。</p>
     *
     * @return 模块目录（去重，顺序稳定：按 pom 声明顺序深度优先）
     * @throws IOException 读 pom 失败
     */
    static List<Path> sourceModuleRoots() throws IOException {
        List<Path> roots = new ArrayList<>();
        collectSourceModules(repoRoot(), roots);
        return List.copyOf(new LinkedHashSet<>(roots));
    }

    /** 深度优先收集含主源码的模块（聚合模块自身有主源码时也一并收集） */
    private static void collectSourceModules(Path moduleRoot, List<Path> sink) throws IOException {
        if (Files.isDirectory(moduleRoot.resolve(MAIN_SOURCE_MARKER))) {
            sink.add(moduleRoot);
        }
        Path pom = moduleRoot.resolve("pom.xml");
        List<String> children = Files.isRegularFile(pom)
            ? allModulesIn(Files.readString(pom, StandardCharsets.UTF_8))
            : List.of();
        for (String child : children) {
            collectSourceModules(moduleRoot.resolve(child), sink);
        }
    }

    /** 一段 pom 文本里<b>所有</b> {@code <modules>} 块（顶层 + 每个 profile）声明的模块名 */
    static List<String> allModulesIn(String pom) {
        Set<String> names = new LinkedHashSet<>();
        int cursor = 0;
        while (cursor < pom.length()) {
            int start = pom.indexOf("<modules>", cursor);
            if (start < 0) {
                break;
            }
            names.addAll(modulesFrom(pom, start));
            cursor = start + 1;
        }
        return List.copyOf(names);
    }

    /**
     * 本仓全部主源码文件（只在本仓模块内，且剪掉嵌套检出的第三方仓库）。
     *
     * @return 主源码文件
     * @throws IOException 遍历失败
     */
    static List<Path> mainSources() throws IOException {
        return sourcesIn("/src/main/java/");
    }

    /**
     * 本仓全部测试源码文件（口径同 {@link #mainSources()}，供「规则只应集中在本模块」校验用）。
     *
     * @return 测试源码文件
     * @throws IOException 遍历失败
     */
    static List<Path> testSources() throws IOException {
        return sourcesIn("/src/test/java/");
    }

    /** 被剪掉的嵌套检出目录（相对仓库根），供自检断言这条保护真的生效 */
    static Set<String> prunedPaths() {
        return Set.copyOf(PRUNED);
    }

    /** 以仓库根为基准的相对路径（统一用 {@code /}） */
    static String relative(Path path) {
        return repoRoot().relativize(path).toString().replace('\\', '/');
    }

    private static List<Path> sourcesIn(String marker) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path moduleRoot : moduleRoots()) {
            Files.walkFileTree(moduleRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(moduleRoot)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (SKIPPED_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    boolean nestedRepo = NESTED_REPO_DIRS.contains(name) || Files.exists(dir.resolve(".git"));
                    if (nestedRepo) {
                        PRUNED.add(relative(dir));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String normalized = file.toString().replace('\\', '/');
                    if (normalized.endsWith(".java") && normalized.contains(marker)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return files;
    }
}
