package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TToponBoundLineMapper;
import com.fc.v2.model.auto.TToponBoundLine;

/**
 * 界线档线：生效窗口、分档压线、优先级 三处口径同根同源 复现用例。
 */
class TToponBoundLineServiceImplTest {

    private TToponBoundLineServiceImpl service;
    private FakeBaseMapper<TToponBoundLine> mapper;

    private static Date at(String s) {
        return Reflect.parse(s);
    }

    private TToponBoundLine line(long id, String code, Integer pri, String start, String end,
                                 String th1, String th2, String th3, Integer status, Integer delFlag) {
        TToponBoundLine r = new TToponBoundLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName("N" + id);
        r.setPriority(pri);
        r.setEffStart(start == null ? null : at(start));
        r.setEffEnd(end == null ? null : at(end));
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new TToponBoundLineServiceImpl();
        mapper = new FakeBaseMapper<>();
        Reflect.setField(service, "toponBoundLineMapper", mapper.as(TToponBoundLineMapper.class));
    }

    /** S8：生效窗口 [effStart, effEnd)，启用日当天算在内，交棒日当天已不在圈里。 */
    @Test
    void effectiveWindow_halfOpen() {
        mapper.seed(line(1, "R1", 1, "2026-01-01 00:00:00", "2026-06-01 00:00:00",
                "10", "50", "100", 0, 0));

        assertEquals(0, service.evaluate("R1", new BigDecimal("5"), at("2025-12-31 23:59:59")));
        assertTrue(service.usable(1L, at("2026-01-01 00:00:00")));
        assertTrue(service.usable(1L, at("2026-05-31 23:59:59")));
        assertFalse(service.usable(1L, at("2026-06-01 00:00:00")));

        assertEquals(1, service.evaluate("R1", new BigDecimal("10"), at("2026-01-01 00:00:00")));
        assertEquals(0, service.evaluate("R1", new BigDecimal("10"), at("2026-06-01 00:00:00")));
    }

    /** 交棒日为空 = 至今有效；启用日为空 = 一直有效直到交棒。 */
    @Test
    void nullBounds_meanOpenEnded() {
        mapper.seed(line(1, "R1", 1, null, null, "10", "50", "100", 0, 0));
        mapper.seed(line(2, "R2", 1, null, "2026-06-01 00:00:00", "10", "50", "100", 0, 0));

        assertTrue(service.usable(1L, at("2099-01-01 00:00:00")));
        assertTrue(service.usable(2L, at("2026-05-31 00:00:00")));
        assertFalse(service.usable(2L, at("2026-06-01 00:00:00")));
    }

    /** S8：同一 ruleCode 多版本，evaluate 必须取 at 时刻生效的那一版，不是 effEnd 最大的。 */
    @Test
    void evaluate_picksVersionEffectiveAt() {
        mapper.seed(line(1, "R1", 1, "2020-01-01 00:00:00", "2026-01-01 00:00:00",
                "10", "50", "100", 1, 0));
        mapper.seed(line(2, "R1", 1, "2026-01-01 00:00:00", null,
                "20", "80", "200", 0, 0));

        assertEquals(1, service.evaluate("R1", new BigDecimal("15"), at("2025-12-01 00:00:00")));
        assertEquals(1, service.evaluate("R1", new BigDecimal("15"), at("2026-01-01 00:00:00")));
        assertEquals(2, service.evaluate("R1", new BigDecimal("25"), at("2026-01-01 00:00:00")));
    }

    /** S5+S8：压线等于上限取本档（两处分档口径一致），evaluate 与 evaluateTop 同根。 */
    @Test
    void thresholdBoundary_sameEverywhere() {
        mapper.seed(line(1, "R1", 1, null, null, "10", "50", "100", 0, 0));

        for (String v : new String[] {"10", "50", "100"}) {
            int e = service.evaluate("R1", new BigDecimal(v), at("2026-09-27 00:00:00"));
            int t = service.evaluateTop(new BigDecimal(v), at("2026-09-27 00:00:00"));
            assertEquals(e, t, "value=" + v);
        }
        assertEquals(1, service.evaluate("R1", new BigDecimal("10"), at("2026-09-27 00:00:00")));
        assertEquals(2, service.evaluate("R1", new BigDecimal("50"), at("2026-09-27 00:00:00")));
        assertEquals(3, service.evaluate("R1", new BigDecimal("100"), at("2026-09-27 00:00:00")));
        assertEquals(4, service.evaluate("R1", new BigDecimal("100.01"), at("2026-09-27 00:00:00")));
    }

    /** S8：evaluateTop / listAvailable / countAvailable / usable 只认 at 时刻在窗口内的当家线。 */
    @Test
    void listAndTop_shareAvailabilityRule() {
        mapper.seed(line(1, "A", 1, "2026-01-01 00:00:00", null, "10", "50", "100", 0, 0));
        mapper.seed(line(2, "B", 5, "2026-01-01 00:00:00", null, "10", "50", "100", 0, 0));
        mapper.seed(line(3, "C", 9, "2020-01-01 00:00:00", "2026-01-01 00:00:00",
                "10", "50", "100", 1, 0));
        mapper.seed(line(4, "D", 9, null, null, "10", "50", "100", 0, 1));

        Date now = at("2026-09-27 00:00:00");
        List<TToponBoundLine> avail = service.listAvailable(now);
        assertEquals(2, avail.size());
        assertEquals(2, service.countAvailable(now));
        assertEquals("B", avail.get(0).getRuleCode());
        assertEquals("A", avail.get(1).getRuleCode());
        assertEquals(2, service.evaluateTop(new BigDecimal("5"), now));
        assertFalse(service.usable(3L, now));
        assertFalse(service.usable(4L, now));
        assertFalse(service.usable(999L, now));
    }

    /** 并列优先级按 ruleCode 降序，结果确定。 */
    @Test
    void priorityTie_codeDesc() {
        mapper.seed(line(1, "AAA", 5, null, null, "10", "50", "100", 0, 0));
        mapper.seed(line(2, "ZZZ", 5, null, null, "10", "50", "100", 0, 0));
        assertEquals("ZZZ", service.listAvailable(at("2026-09-27 00:00:00")).get(0).getRuleCode());
    }
}
