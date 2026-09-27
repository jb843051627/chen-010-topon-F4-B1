package com.fc.v2.topon;

import com.fc.v2.mapper.auto.TToponBoundLineMapper;
import com.fc.v2.model.auto.TToponBoundLine;
import com.fc.v2.service.impl.TToponBoundLineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 界线勘定基准档线（rule-eval）复现测试。
 *
 * 线索对照：
 *  - "哪一段算在圈里，两个门判得两样"（evaluate 无越界判据，evaluateTop 有）
 *  - "这条复核链两处口径分叉，判据该同根同源"（usable/countAvailable 与 listAvailable 各说各话）
 *  - 生效窗口（启用日含、交棒日不含）无人把守；优先级排序形同虚设
 */
class TToponBoundLineServiceImplTest {

    private TToponBoundLineMapper mapper;
    private TToponBoundLineServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TToponBoundLineMapper.class);
        service = new TToponBoundLineServiceImpl();
        ReflectionTestUtils.setField(service, "toponBoundLineMapper", mapper);
    }

    private static Date at(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TToponBoundLine rule(long id, String code, int priority, int status, int delFlag,
                                        String effStart, String effEnd, String th1, String th2, String th3) {
        TToponBoundLine r = new TToponBoundLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(delFlag);
        r.setEffStart(effStart == null ? null : at(effStart));
        r.setEffEnd(effEnd == null ? null : at(effEnd));
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        return r;
    }

    private void stubAll(List<TToponBoundLine> rules) {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponBoundLine>(rules));
    }

    /** at 时刻可用 = 未删 + 当家 + 启用日(含) <= at < 交棒日(不含)；按优先级降序、ruleCode 降序 */
    @Test
    void listAvailable_filtersWindowStatusAndSorts() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-01-01 00:00:00", "2026-12-31 00:00:00", "10", "20", "30"),
                rule(2L, "B", 5, 0, 0, "2026-01-01 00:00:00", null, "5", "8", "9"),
                rule(3L, "C", 9, 1, 0, "2026-01-01 00:00:00", null, "5", "8", "9"),   // 已让位
                rule(4L, "D", 9, 0, 1, "2026-01-01 00:00:00", null, "5", "8", "9"),   // 已删除
                rule(5L, "E", 9, 0, 0, "2026-10-01 00:00:00", null, "5", "8", "9"),   // 未启用
                rule(6L, "F", 9, 0, 0, "2025-01-01 00:00:00", "2026-09-01 00:00:00", "5", "8", "9") // 已交棒
        ));
        List<TToponBoundLine> avail = service.listAvailable(at("2026-09-27 12:00:00"));
        assertEquals(2, avail.size(), "窗外/让位/已删的规则不应入列");
        assertEquals(Long.valueOf(2L), avail.get(0).getId(), "优先级高者应排前");
        assertEquals(Long.valueOf(1L), avail.get(1).getId());
    }

    /** 交棒日（不含）：启用日当日可用，交棒日当日即不可用 */
    @Test
    void listAvailable_effStartInclusive_effEndExclusive() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-09-27 12:00:00", null, "10", "20", "30"),   // 启用日=此刻
                rule(2L, "B", 1, 0, 0, "2026-01-01 00:00:00", "2026-09-27 12:00:00", "10", "20", "30") // 交棒日=此刻
        ));
        List<TToponBoundLine> avail = service.listAvailable(at("2026-09-27 12:00:00"));
        assertEquals(1, avail.size());
        assertEquals(Long.valueOf(1L), avail.get(0).getId(), "交棒日（不含）当日不应再当家");
    }

    /** 单条 usable 与列表同根同源 */
    @Test
    void usable_matchesListCaliber() {
        TToponBoundLine ok = rule(2L, "B", 5, 0, 0, "2026-01-01 00:00:00", null, "5", "8", "9");
        TToponBoundLine future = rule(5L, "E", 9, 0, 0, "2026-10-01 00:00:00", null, "5", "8", "9");
        TToponBoundLine retired = rule(3L, "C", 9, 1, 0, "2026-01-01 00:00:00", null, "5", "8", "9");
        when(mapper.selectById(2L)).thenReturn(ok);
        when(mapper.selectById(5L)).thenReturn(future);
        when(mapper.selectById(3L)).thenReturn(retired);
        Date at = at("2026-09-27 12:00:00");
        assertTrue(service.usable(2L, at));
        assertFalse(service.usable(5L, at), "未启用的规则不应判可用");
        assertFalse(service.usable(3L, at), "已让位的规则不应判可用");
    }

    /** 可用条数与列表同根同源 */
    @Test
    void countAvailable_matchesList() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-01-01 00:00:00", null, "10", "20", "30"),
                rule(2L, "B", 5, 1, 0, "2026-01-01 00:00:00", null, "5", "8", "9"), // 已让位
                rule(3L, "C", 9, 0, 0, "2026-10-01 00:00:00", null, "5", "8", "9")  // 未启用
        ));
        Date at = at("2026-09-27 12:00:00");
        assertEquals(service.listAvailable(at).size(), service.countAvailable(at), "角标与列表必须同口径");
        assertEquals(1, service.countAvailable(at));
    }

    /** evaluateTop 取 at 时刻可用规则中优先级最高者 */
    @Test
    void evaluateTop_picksHighestPriorityRule() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-01-01 00:00:00", null, "10", "20", "30"),
                rule(2L, "B", 5, 0, 0, "2026-01-01 00:00:00", null, "5", "8", "9")));
        // 输入 6：按 B（优先级 5）应落 2 档；若错取 A（优先级 1）则落 1 档
        assertEquals(2, service.evaluateTop(new BigDecimal("6"), at("2026-09-27 12:00:00")),
                "应按优先级最高者判档");
    }

    /** 两个门同一把尺：evaluate 与 evaluateTop 的越界判据一致（[0,100] 之外回 0） */
    @Test
    void evaluate_outOfRange_returnsZeroLikeEvaluateTop() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-01-01 00:00:00", null, "10", "20", "30")));
        Date at = at("2026-09-27 12:00:00");
        assertEquals(0, service.evaluate("A", new BigDecimal("101"), at), "越界输入应回 0");
        assertEquals(0, service.evaluate("A", new BigDecimal("-1"), at), "越界输入应回 0");
        assertEquals(0, service.evaluateTop(new BigDecimal("101"), at));
    }

    /** 无 at 时刻生效版本返回 0（不得捡"交棒日最新"的僵尸版本） */
    @Test
    void evaluate_noEffectiveVersion_returnsZero() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-10-01 00:00:00", null, "10", "20", "30"),          // 未启用
                rule(2L, "A", 1, 0, 0, "2025-01-01 00:00:00", "2026-09-01 00:00:00", "1", "2", "3") // 已交棒
        ));
        assertEquals(0, service.evaluate("A", new BigDecimal("5"), at("2026-09-27 12:00:00")),
                "无生效版本应回 0");
    }

    /** 生效版本判档：取 at 时刻生效的那一版 */
    @Test
    void evaluate_picksVersionEffectiveAt() {
        stubAll(Arrays.asList(
                rule(1L, "A", 1, 0, 0, "2026-01-01 00:00:00", "2026-06-01 00:00:00", "1", "2", "3"),
                rule(2L, "A", 1, 0, 0, "2026-06-01 00:00:00", null, "10", "20", "30")));
        // 输入 5：生效版（6月起）落 1 档；僵尸版（6月前）落 4 档
        assertEquals(1, service.evaluate("A", new BigDecimal("5"), at("2026-09-27 12:00:00")));
    }
}
