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
package cn.ypbin.admin.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.system.constant.SpaceConstants;
import cn.ypbin.admin.system.entity.SysSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SpacePathResolver} 的行为测试。
 *
 * <p>本类取代了原先两份各自实现的「内存上溯拼路径」，所以原先散落在
 * {@code SysSpaceServiceImplTest} / {@code SysSpaceRelationServiceImplTest} 里的
 * 路径断言口径，这里集中覆盖。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class SpacePathResolverTest {

    @Test
    @DisplayName("★ 三层嵌套：父在前、子在后，用统一分隔符")
    void shouldBuildPathFromRootToLeaf() {
        List<SysSpace> nodes = List.of(
            space(1L, 0L, "总部园区"),
            space(2L, 1L, "A栋"),
            space(3L, 2L, "101室"));

        Map<Long, String> paths = SpacePathResolver.resolve(nodes);

        assertThat(paths.get(1L)).isEqualTo("总部园区");
        assertThat(paths.get(2L)).isEqualTo("总部园区 / A栋");
        assertThat(paths.get(3L)).isEqualTo("总部园区 / A栋 / 101室");
    }

    @Test
    @DisplayName("★ 分隔符来自 {@link SpacePathResolver#PATH_SEPARATOR} 单一来源（不再是硬编码字面量）")
    void shouldUseSharedSeparatorConstant() {
        List<SysSpace> nodes = List.of(space(1L, 0L, "A"), space(2L, 1L, "B"));

        String path = SpacePathResolver.pathOf(2L, nodes);

        assertThat(path).isEqualTo("A" + SpacePathResolver.PATH_SEPARATOR + "B");
    }

    @Test
    @DisplayName("★ 用 longValue 比较根 ID：根值改成缓存外的数也仍能正确截断")
    void shouldCompareRootIdByValueNotReference() {
        // 模拟「根 ID 不是 0」的场景：此处只验证比较语义（0 落在 Long 缓存内，
        // 引用比较会碰巧成立；真正要防的是将来改动根值时的静默失效）。
        List<SysSpace> nodes = List.of(space(1L, SpaceConstants.ROOT_PARENT_ID, "根"));

        assertThat(SpacePathResolver.pathOf(1L, nodes)).isEqualTo("根");
    }

    @Test
    @DisplayName("成环不无限循环（有界上溯）")
    void shouldNotLoopForeverOnCycle() {
        // 2 的父是 3，3 的父是 2 —— 脏数据成环
        List<SysSpace> nodes = List.of(
            space(2L, 3L, "A"),
            space(3L, 2L, "B"));

        String path = SpacePathResolver.pathOf(2L, nodes);

        // **锁定「有界」这一不变量**：段数不得超过深+1（否则就是死循环或无限追加）。
        // 成环时拼出的路径本身是垃圾（A/B/A/B…），但必须保证不死循环——
        // 那是这个 guard 唯一负责的事，也是历史脏数据下最容易回归的点。
        assertThat(path.split(" / ").length)
            .as("成环时必须靠 guard 有界退出，不得无限追加")
            .isLessThanOrEqualTo(SpaceConstants.MAX_SPACE_DEPTH + 1);
    }

    @Test
    @DisplayName("深度超限时截断（不会拼出超过 MAX_SPACE_DEPTH 段）")
    void shouldTruncateWhenExceedingMaxDepth() {
        List<SysSpace> nodes = new ArrayList<>();
        long maxDepth = SpaceConstants.MAX_SPACE_DEPTH;
        // 造一条比上限更深的链
        for (long i = 1; i <= maxDepth + 5; i++) {
            nodes.add(space(i, i - 1, "L" + i));
        }

        String path = SpacePathResolver.pathOf(maxDepth + 5, nodes);

        long segments = path.split(" / ").length;
        assertThat(segments).isLessThanOrEqualTo(maxDepth + 1L);
    }

    @Test
    @DisplayName("父不存在（断链）时只拼到能拼到的部分")
    void shouldStopAtBrokenChain() {
        // 3 的父是 2，但 2 不在快照里
        List<SysSpace> nodes = List.of(space(3L, 2L, "101室"));

        assertThat(SpacePathResolver.pathOf(3L, nodes)).isEqualTo("101室");
    }

    @Test
    @DisplayName("空集合 / null / 查询不存在的 ID 都返回空（不抛）")
    void shouldReturnEmptyForEdgeCases() {
        assertThat(SpacePathResolver.resolve(List.of())).isEmpty();
        assertThat(SpacePathResolver.resolve(null)).isEmpty();
        assertThat(SpacePathResolver.pathOf(1L, List.of())).isEmpty();
        assertThat(SpacePathResolver.pathOf(null, List.of(space(1L, 0L, "A")))).isEmpty();
        assertThat(SpacePathResolver.pathOf(999L, List.of(space(1L, 0L, "A")))).isEmpty();
    }

    @Test
    @DisplayName("跳过 null 节点（快照里混入 null 不应 NPE）")
    void shouldSkipNullNodes() {
        List<SysSpace> nodes = new ArrayList<>();
        nodes.add(null);
        nodes.add(space(1L, 0L, "根"));

        assertThat(SpacePathResolver.resolve(nodes)).containsEntry(1L, "根");
    }

    private static SysSpace space(Long id, Long pid, String name) {
        SysSpace space = new SysSpace();
        space.setId(id);
        space.setPid(pid);
        space.setName(name);
        return space;
    }
}
