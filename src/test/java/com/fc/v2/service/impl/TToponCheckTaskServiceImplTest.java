package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TToponCheckTaskMapper;
import com.fc.v2.model.auto.TToponCheckTask;

/**
 * 定期检验派发条目：到期扫描/执行窗口/状态回写/逐条容错 复现用例。
 */
class TToponCheckTaskServiceImplTest {

    private TToponCheckTaskServiceImpl service;
    private FakeBaseMapper<TToponCheckTask> mapper;

    private static Date at(String s) {
        return Reflect.parse(s);
    }

    private TToponCheckTask task(long id, String dueAt, String amount, Integer status, Integer delFlag) {
        TToponCheckTask r = new TToponCheckTask();
        r.setId(id);
        r.setItemNo("ITEM-" + id);
        r.setDueAt(at(dueAt));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new TToponCheckTaskServiceImpl();
        mapper = new FakeBaseMapper<>();
        Reflect.setField(service, "toponCheckTaskMapper", mapper.as(TToponCheckTaskMapper.class));
    }

    /** S1：本轮一条都没到期，属正常空轮，回执应报 0（成功），不得抛故障。 */
    @Test
    void emptyRound_returnsZero_withoutThrowing() {
        assertEquals(0, service.runOnce(at("2026-09-27 03:00:00")));

        mapper.seed(task(1, "2026-09-28 00:00:00", "3", 0, 0));
        assertEquals(0, service.runOnce(at("2026-09-27 03:00:00")));
    }

    /** S5：到期账压线（dueAt 恰好等于扫描时刻）必须算到期，不能少个等号。 */
    @Test
    void exactBoundary_isDue() {
        mapper.seed(task(1, "2026-09-27 03:00:00", "3", 0, 0));
        List<TToponCheckTask> due = service.listDue(at("2026-09-27 03:00:00"));
        assertEquals(1, due.size());
    }

    /** S7：执行时段闸门 [02:00,05:00) 必须生效，闸门外随叫随扫不得拾掇。 */
    @Test
    void executionWindow_gateEnforced() {
        mapper.seed(task(1, "2026-09-01 00:00:00", "3", 0, 0));

        assertEquals(1, service.listDue(at("2026-09-27 02:00:00")).size());
        assertEquals(1, service.listDue(at("2026-09-27 04:59:59")).size());
        assertEquals(0, service.listDue(at("2026-09-27 05:00:00")).size());
        assertEquals(0, service.listDue(at("2026-09-27 01:59:59")).size());
        assertEquals(0, service.listDue(at("2026-09-27 23:00:00")).size());
    }

    /** S9：只捞待派(status=0)且未删除的条目；已派/派不出去/删除的都不得再捞。 */
    @Test
    void onlyWaitingAndAlive_picked() {
        mapper.seed(task(1, "2026-09-01 00:00:00", "3", 0, 0));
        mapper.seed(task(2, "2026-09-01 00:00:00", "3", 1, 0));
        mapper.seed(task(3, "2026-09-01 00:00:00", "-1", 2, 0));
        mapper.seed(task(4, "2026-09-01 00:00:00", "3", 0, 1));

        List<TToponCheckTask> due = service.listDue(at("2026-09-27 03:00:00"));
        assertEquals(1, due.size());
        assertEquals(Long.valueOf(1L), due.get(0).getId());
    }

    /** S3+S9：办妥的条目必须回写状态，下一轮扫描不得再捞出来重办。 */
    @Test
    void doneItems_persisted_andNotReprocessed() {
        mapper.seed(task(1, "2026-09-01 00:00:00", "3", 0, 0));
        assertEquals(1, service.runOnce(at("2026-09-27 03:00:00")));
        assertEquals(Integer.valueOf(1), mapper.stored(1L).getStatus());

        assertEquals(0, service.runOnce(at("2026-09-27 03:00:00")));
    }

    /** S11：单条撂挑子（amount 为空/为负）只记该条派不出去，整轮不得停摆。 */
    @Test
    void singleBadItem_doesNotStopRound() {
        mapper.seed(task(1, "2026-09-01 00:00:00", "3", 0, 0));
        mapper.seed(task(2, "2026-09-01 00:00:00", null, 0, 0));
        mapper.seed(task(3, "2026-09-01 00:00:00", "-2", 0, 0));

        int ok = service.runOnce(at("2026-09-27 03:00:00"));
        assertEquals(1, ok);
        assertEquals(Integer.valueOf(1), mapper.stored(1L).getStatus());
        assertEquals(Integer.valueOf(2), mapper.stored(2L).getStatus());
        assertEquals(Integer.valueOf(2), mapper.stored(3L).getStatus());
    }

}
