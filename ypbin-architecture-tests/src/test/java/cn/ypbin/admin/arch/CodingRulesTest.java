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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * admin 仓「字节码级」架构约束：这些规则在字节码上判定比源码正则更可靠。
 *
 * <p>与 {@link SourceConventionTest} 的分工原则：能看字节码就别扫源码（源码正则会漏判「注解与签名同行」
 * 「通配导入」「泛型/raw 类型」等写法）。反过来，Lombok {@code @Data}（SOURCE 保留）、
 * {@code switch(enum)} 编译出的 {@code ordinal()}、循环边界这三类在字节码上<b>不可表达或必然误报</b>，
 * 才落到源码扫描——这个划分沿用 starter 侧已踩过坑的结论。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class CodingRulesTest {

    /** Spring 事务注解全限定名（精确匹配，避免把 @TransactionalEventListener 误当作 @Transactional） */
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /** 字段注入注解全限定名 */
    private static final List<String> FIELD_INJECTION_ANNOTATIONS = List.of(
        "org.springframework.beans.factory.annotation.Autowired",
        "jakarta.annotation.Resource");

    /** 谓词：调用 printStackTrace()（owner 多为 Throwable 子类，故按可赋值判定） */
    private static final DescribedPredicate<JavaMethodCall> CALLS_PRINT_STACK_TRACE =
        DescribedPredicate.describe("调用 printStackTrace()", call ->
            call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    /** 谓词：访问 System.out */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_OUT =
        DescribedPredicate.describe("访问 System.out", access ->
            access.getName().equals("out") && access.getTargetOwner().isAssignableTo(System.class));

    /** 谓词：访问 System.err */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_ERR =
        DescribedPredicate.describe("访问 System.err", access ->
            access.getName().equals("err") && access.getTargetOwner().isAssignableTo(System.class));

    /** 业务实现层包（各业务域的 {@code service/impl}） */
    private static final String SERVICE_IMPL_PACKAGE = "..service.impl..";

    /** 宿主端口适配层包（各业务域的 {@code provider}） */
    private static final String PROVIDER_PACKAGE = "..provider..";

    /**
     * 覆盖下界①：导入的 admin 主源码类总数。
     *
     * <p><b>依据</b>（2026-09-18 本机实测，两种口径互相印证）：{@code target/classes} 形态下
     * {@link ClassFileImporter} 导入 <b>392</b> 个类，与「7 个业务模块 {@code target/classes} 下
     * {@code cn/ypbin/admin} 的 {@code .class} 文件数」逐模块核对一致
     * （common 11 / gateway 2 / auth 17 / system 153 / ai 53 / system-api 121 / ai-api 35 = 392）；
     * 而 fat jar 进入测试类路径的形态下只剩 <b>167</b> 个 = 392 − (gateway 2 + auth 17 + system 153
     * + ai 53)——即仓里 4 个带 {@code repackage} 的模块整块消失，只剩 common / system-api /
     * ai-api 三个非 fat jar 模块可见。</p>
     *
     * <p>取实测值而非「留余量的估值」：少一个类都说明导入范围被削。因业务代码删除导致合法下降时，
     * 必须<b>同步下调本常量并在此写明理由</b>，不得靠放宽断言蒙混过去。</p>
     */
    private static final int MIN_IMPORTED_CLASSES = 392;

    /** 覆盖下界②：{@code ..service.impl..} 类数（同上实测 40；fat jar 形态下为 0，即事故靶心）。 */
    private static final int MIN_SERVICE_IMPL_CLASSES = 40;

    /**
     * 覆盖下界③：{@code ..provider..} 类数（同上实测 15；fat jar 形态下仅剩 2，即 {@code ypbin-common}
     * 的那两个——绝大多数适配实现都藏在 fat jar 里）。规则的目标侧漏了，规则就恒真（永远绿）。
     */
    private static final int MIN_PROVIDER_CLASSES = 15;

    /**
     * 规则：{@code service/impl} 不得依赖 {@code provider}。
     *
     * <p><strong>为什么需要这条</strong>：{@code provider} 是「宿主按 starter 端口契约给出的适配实现」
     * （数据范围、字典、敏感词、日志等），{@code service/impl} 是业务实现层。实现层直接依赖适配实现类，
     * 会把「实现层 → 适配层」的依赖方向倒置成硬耦合：适配实现一旦换实现（换库、换缓存、换策略）
     * 就要动业务实现，且两者之间无法独立测试与替换。</p>
     *
     * <p><strong>这是一次真实回归的护栏</strong>（2026-09-17）：{@code SysUserServiceImpl} 曾直接持有
     * {@code provider.AdminDataScopeHandler} 以复用「该部门是否在数据范围内」的判定，导致写入路径与
     * 读取路径共用同一个具体实现类；上一轮重构把该判定抽为 {@code service/support} 下的共享能力
     * （{@code DataScopeResolver}/{@code AbstractDataScopeResolver}），{@code provider} 只保留端口适配。
     * 本条规则把「不许倒回去」固化成构建失败，而不是靠 review 记得。</p>
     *
     * <p><strong>方向性</strong>：只禁止 {@code service/impl → provider}。反向（{@code provider} 里的适配器
     * 依赖 {@code service/impl}）不在本条约束内，由 {@code ArchRuleSelfCheckTest} 显式断言不被误报。</p>
     *
     * <p><strong>为什么用包名通配而非写死模块</strong>：admin 是多业务域仓（{@code system} / {@code ai} /
     * 未来的域），写死 {@code cn.ypbin.admin.system} 会让新域的同类违规静默逃逸——正是本仓
     * 「门禁必须覆盖完整」的一贯口径。</p>
     *
     * @return 规则本体（门禁与「有效性自检」共用同一对象，避免自检验的是副本）
     */
    static ArchRule serviceImplShouldNotDependOnProvider() {
        return noClasses()
            .that().resideInAPackage(SERVICE_IMPL_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(PROVIDER_PACKAGE)
            .because("service/impl 是业务实现层、provider 是宿主端口适配层；实现层直接依赖适配实现类会把"
                + "「实现层 → 适配层」倒置成硬耦合（历史回归：SysUserServiceImpl 依赖 provider.AdminDataScopeHandler）。"
                + "共享能力请抽到 service/support（如 DataScopeResolver），provider 只保留端口适配");
    }

    private static JavaClasses classes;

    /**
     * 显式导入各业务模块主源码的编译输出目录（{@code <module>/target/classes}）。
     *
     * <p><strong>为什么不 {@code importPackages("cn.ypbin.admin")}</strong>（2026-09-18 CI 事故）：
     * 本仓共 4 个模块（{@code ypbin-system} / {@code ypbin-ai} / {@code ypbin-gateway} /
     * {@code ypbin-auth}）的 {@code repackage} 未配 classifier，主构件被打成
     * {@code BOOT-INF/classes} 布局的 fat jar。构建走到 {@code package}/{@code verify} 时测试类路径
     * 拿到的是这个 fat jar，jar 根下没有 {@code cn/ypbin/admin} 条目 ⇒
     * {@code importPackages} 一个业务类都看不到 ⇒ {@code ..service.impl..} 选中 0 个类 ⇒
     * ArchUnit 报「failed to check any classes」；而只跑到 {@code test} 时拿到 {@code target/classes}
     * 又一切正常 ⇒ <b>门禁结果依赖打包形态</b>。改为按模块目录导入后，两种形态得到<b>同一份</b>字节码。</p>
     *
     * <p>模块清单由聚合 pom 递归推导（{@link SourceScan#sourceModuleRoots()}），新增模块自动纳入；
     * 任一模块没有 {@code target/classes} 时<b>显式失败</b>而不是缩水导入（禁静默降级）。</p>
     */
    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(moduleClassesDirs());
    }

    /**
     * 各业务模块主源码的 {@code target/classes} 目录。
     *
     * @return 目录清单（存在性已校验）
     */
    private static List<Path> moduleClassesDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        try {
            for (Path moduleRoot : SourceScan.sourceModuleRoots()) {
                Path classesDir = moduleRoot.resolve(SourceScan.CLASSES_DIR);
                if (Files.isDirectory(classesDir)) {
                    dirs.add(classesDir);
                } else {
                    missing.add(SourceScan.relative(moduleRoot));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("推导业务模块目录失败", ex);
        }
        // 先报缺失（含「全部缺失」的情形，例如 fresh clone 下裸 `-pl` 不带 -am），否则列清单的提示不可达
        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下模块没有 " + SourceScan.CLASSES_DIR
                + "，架构测试无法导入其字节码：" + missing
                + "；请用 `mvn -pl ypbin-architecture-tests -am test`（缺 -am 时兄弟模块从 ~/.m2 解析，"
                + "本地可能只有已 repackage 的 fat jar，且没有 target/classes）");
        }
        if (dirs.isEmpty()) {
            throw new IllegalStateException("聚合 pom 的模块清单里没有含 src/main/java 的模块（仓库根："
                + SourceScan.repoRoot() + "）");
        }
        return List.copyOf(dirs);
    }

    /** 按 ArchUnit 的包谓词计数（与规则选类的语义完全一致，故它就是「规则实际检查了多少类」） */
    private static long countInPackage(String packagePattern) {
        DescribedPredicate<JavaClass> predicate = JavaClass.Predicates.resideInAPackage(packagePattern);
        return classes.stream().filter(predicate).count();
    }

    @Test
    @DisplayName("覆盖自检：字节码导入必须真的看到业务模块的类（防「0 类空转」的假绿）")
    void importedClassesShouldCoverBusinessModules() {
        assertThat(classes.size())
            .as("导入的 admin 主源码类总数不得低于实测下界；降低说明导入范围被削（例如又退回 fat jar 形态）")
            .isGreaterThanOrEqualTo(MIN_IMPORTED_CLASSES);
        assertThat(countInPackage(SERVICE_IMPL_PACKAGE))
            .as("`" + SERVICE_IMPL_PACKAGE + "` 是「service/impl 禁依赖 provider」规则的选类条件；"
                + "为 0 时 ArchUnit 会直接报 failed to check any classes，门禁变成空转")
            .isGreaterThanOrEqualTo(MIN_SERVICE_IMPL_CLASSES);
        assertThat(countInPackage(PROVIDER_PACKAGE))
            .as("`" + PROVIDER_PACKAGE + "` 是上述规则的目标侧包；看不到它规则恒真（永远绿）")
            .isGreaterThanOrEqualTo(MIN_PROVIDER_CLASSES);
    }

    @Test
    @DisplayName("覆盖自检：每个业务模块都必须真的贡献了被检查的类（防「模块级」空转）")
    void everySourceModuleShouldContributeClasses() {
        List<String> empty = new ArrayList<>();
        for (Path dir : moduleClassesDirs()) {
            String prefix = dir.toUri().toString();
            boolean contributed = classes.stream().anyMatch(clazz -> clazz.getSource()
                .map(source -> source.getUri().toString().startsWith(prefix))
                .orElse(false));
            if (!contributed) {
                empty.add(SourceScan.relative(dir));
            }
        }
        assertThat(empty)
            .as("以下模块的 target/classes 一个类都没进分析范围：模块清单推导或导入路径已失效")
            .isEmpty();
    }

    @Test
    @DisplayName("禁止 printStackTrace 与直接使用 System.out/System.err")
    void shouldNotPrintStackTraceOrUseSystemStreams() {
        // 用 callMethodWhere 而非 callMethod(Throwable.class, ...)：调用点 owner 通常是子类
        // （如 Exception/IllegalStateException），按精确 owner 匹配会静默漏掉全部违规
        noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE)
            .because("必须走日志框架并传完整堆栈（log.error(\"...\", ex)），printStackTrace 丢失日志上下文")
            .check(classes);

        noClasses()
            .should().accessFieldWhere(ACCESSES_SYSTEM_OUT)
            .orShould().accessFieldWhere(ACCESSES_SYSTEM_ERR)
            .because("标准输出绕过日志框架与统一格式，生产环境不可追踪")
            .check(classes);
    }

    @Test
    @DisplayName("@Transactional 必须显式声明 rollbackFor（方法级与类级都要，否则受检异常漏回滚）")
    void transactionalShouldAlwaysDeclareRollbackFor() {
        List<String> violations = transactionalMembersWithoutRollbackFor(classes);
        assertThat(violations)
            .as("写操作的 @Transactional 必须带 rollbackFor = Exception.class；"
                + "注意 @TransactionalEventListener 不是 @Transactional，不该被判违规")
            .isEmpty();
    }

    @Test
    @DisplayName("禁止字段注入（@Autowired/@Resource 标注字段），统一构造器注入")
    void shouldNotUseFieldInjection() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            for (JavaField field : clazz.getFields()) {
                boolean injected = field.getAnnotations().stream()
                    .anyMatch(annotation -> FIELD_INJECTION_ANNOTATIONS.contains(annotation.getRawType().getName()));
                if (injected) {
                    violations.add(clazz.getName() + "#" + field.getName());
                }
            }
        }
        assertThat(violations)
            .as("字段注入让依赖不可变性与可测性变差（本仓统一 @RequiredArgsConstructor + final 字段）")
            .isEmpty();
    }

    @Test
    @DisplayName("service/impl 包禁止依赖 provider 包（实现层不得直接依赖宿主端口适配实现）")
    void serviceImplShouldNotDependOnProviderPackages() {
        serviceImplShouldNotDependOnProvider().check(classes);
    }

    /**
     * 判定注解是否**显式**声明了 rollbackFor。
     *
     * <p>⚠️ 这里不能用 {@code getProperties().containsKey("rollbackFor")}：ArchUnit 的
     * {@code getProperties()} <b>会把注解默认值一并填进来</b>——实测未写任何属性的
     * {@code @Transactional} 其 props 里就已有 {@code rollbackFor=[]}（空数组），
     * 于是「containsKey」判定恒为真，规则<b>永远通过（恒真）</b>，是典型的假绿。
     * 正确做法是按<b>取值</b>判：{@code rollbackFor} 的默认值是空数组，显式声明后非空。</p>
     *
     * <p>本方法只认 {@code rollbackFor}（铁律的字面要求）。仅写 {@code rollbackForClassName}
     * 的写法会被判为违规——本仓 2026-09-16 实测 0 处该写法。</p>
     *
     * @param annotation 注解实例
     * @return 显式声明了非空 rollbackFor 时返回 true
     */
    static boolean declaresRollbackFor(JavaAnnotation<?> annotation) {
        Object value = annotation.get("rollbackFor").orElse(null);
        if (value instanceof Object[] declared) {
            // JavaClass[] 也是 Object[]，长度即「显式声明了几个回滚异常类型」
            return declared.length > 0;
        }
        return value != null;
    }

    static List<String> transactionalMembersWithoutRollbackFor(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            // 类级 @Transactional 会作用于该类全部方法，缺 rollbackFor 同样在受检异常上漏回滚
            List<JavaAnnotation<JavaClass>> classAnnotations = clazz.getAnnotations().stream()
                .filter(annotation -> annotation.getRawType().getName().equals(TRANSACTIONAL))
                .toList();
            if (!classAnnotations.isEmpty()
                && !classAnnotations.stream().anyMatch(CodingRulesTest::declaresRollbackFor)) {
                violations.add(clazz.getName() + "（类级注解）");
            }
            for (JavaMethod method : clazz.getMethods()) {
                List<JavaAnnotation<JavaMethod>> annotations = method.getAnnotations().stream()
                    .filter(annotation -> annotation.getRawType().getName().equals(TRANSACTIONAL))
                    .toList();
                if (annotations.isEmpty()) {
                    continue;
                }
                boolean declared = annotations.stream().anyMatch(CodingRulesTest::declaresRollbackFor);
                if (!declared) {
                    violations.add(clazz.getName() + "#" + method.getName());
                }
            }
        }
        return violations;
    }
}
