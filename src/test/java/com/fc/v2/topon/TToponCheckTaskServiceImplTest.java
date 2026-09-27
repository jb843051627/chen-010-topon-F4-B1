package com.fc.v2.topon;

import com.fc.v2.mapper.auto.TToponCheckTaskMapper;
import com.fc.v2.model.auto.TToponCheckTask;
import com.fc.v2.service.impl.TToponCheckTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定期检验派发条目（scheduling-job）复现测试。
 *
 * 线索对照：
 *  - "到期那笔账少了个等号，压线的全被放过"
 *  - "执行时段这层闸门压根没设，条目随叫随被拾掇"
 *  - "办妥的条目下一轮扫描又捞出来重办一过"
 *  - "本轮查无到期条目，回执报的却是故障而非零件"
 *  - "条目明明动过，状态栏仍蹲在原地"
 *  - "单有一项撂了挑子，整轮执行说停就停"
 */
class TToponCheckTaskServiceImplTest {

    private TToponCheckTaskMapper mapper;
    private TToponCheckTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TToponCheckTaskMapper.class);
        service = new TToponCheckTaskServiceImpl();
        ReflectionTestUtils.setField(service, "toponCheckTaskMapper", mapper);
    }

    private static Date at(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TToponCheckTask task(long id, String dueAt, Integer status, int delFlag, BigDecimal amount) {
        TToponCheckTask t = new TToponCheckTask();
        t.setId(id);
        t.setItemNo("ITEM-" + id);
        t.setDueAt(dueAt == null ? null : at(dueAt));
        t.setStatus(status);
        t.setDelFlag(delFlag);
        t.setAmount(amount);
        return t;
    }

    /** 压线（due_at == 扫描时刻）的条目应当到期 */
    @Test
    void listDue_includesItemExactlyAtDeadline() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponCheckTask>(Collections.singletonList(
                task(1L, "2026-09-27 03:00:00", 0, 0, new BigDecimal("3")))));
        List<TToponCheckTask> due = service.listDue(at("2026-09-27 03:00:00"));
        assertEquals(1, due.size(), "恰好到期的条目被放过（少了个等号）");
    }

    /** 执行时段（02:00 含 — 05:00 不含）之外不得拾掇条目 */
    @Test
    void listDue_outsideExecutionWindow_returnsEmpty() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponCheckTask>(Collections.singletonList(
                task(1L, "2026-09-27 00:00:00", 0, 0, new BigDecimal("3")))));
        assertTrue(service.listDue(at("2026-09-27 10:00:00")).isEmpty(), "窗口外（10点）不应拾取");
        assertTrue(service.listDue(at("2026-09-27 05:00:00")).isEmpty(), "窗口末端（05:00，不含）不应拾取");
        assertEquals(1, service.listDue(at("2026-09-27 02:00:00")).size(), "窗口起点（02:00，含）应拾取");
    }

    /** 已派/已删的条目下一轮不得再捞出来 */
    @Test
    void listDue_skipsDoneAndDeleted() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponCheckTask>(Arrays.asList(
                task(1L, "2026-09-27 01:00:00", 1, 0, new BigDecimal("3")), // 已派
                task(2L, "2026-09-27 01:00:00", 0, 1, new BigDecimal("3")), // 已删除
                task(3L, "2026-09-27 01:00:00", 0, 0, new BigDecimal("3"))  // 待派
        )));
        List<TToponCheckTask> due = service.listDue(at("2026-09-27 03:00:00"));
        assertEquals(1, due.size(), "已办妥/已删除的条目被重复捞出");
        assertEquals(Long.valueOf(3L), due.get(0).getId());
    }

    /** 本轮查无到期条目：回执应是 0 条，而不是抛故障 */
    @Test
    void runOnce_noDueItems_returnsZeroInsteadOfThrowing() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponCheckTask>());
        assertEquals(0, service.runOnce(at("2026-09-27 03:00:00")), "空轮次应回 0 而非抛异常");
    }

    /** 处理过的条目状态要落库；单条撂挑子不得拖垮整轮 */
    @Test
    void runOnce_marksStatusAndContinuesAfterBadItem() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponCheckTask>(Arrays.asList(
                task(1L, "2026-09-27 01:00:00", 0, 0, null),                 // amount 缺失 → 派不出去(2)
                task(2L, "2026-09-27 01:00:00", 0, 0, new BigDecimal("2"))  // 正常 → 已派(1)
        )));
        int ok = service.runOnce(at("2026-09-27 03:00:00"));
        assertEquals(1, ok, "单条失败应跳过继续，成功条数为 1");

        ArgumentCaptor<TToponCheckTask> captor = ArgumentCaptor.forClass(TToponCheckTask.class);
        verify(mapper, times(2)).updateById(captor.capture());
        List<TToponCheckTask> updated = captor.getAllValues();
        assertEquals(Integer.valueOf(2), updated.get(0).getStatus(), "坏条目应置 派不出去(2)");
        assertEquals(Integer.valueOf(1), updated.get(1).getStatus(), "好条目应置 已派(1)");
    }
}
