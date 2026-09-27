package com.fc.v2.topon;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponSubmitRowMapper;
import com.fc.v2.model.auto.TToponSubmitRow;
import com.fc.v2.service.impl.TToponSubmitRowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 地名数据汇交核收行（batch-process）复现测试。
 *
 * 接口契约：逐行校验，合法的入库、非法的记入失败明细（保留原始行号），返回成功行数；
 * 同一批次重复提交不得重复入库；行数超过单批上限返回 -1 且整批不入库；空批次返回 0；
 * 失败明细按行号升序。
 *
 * 线索对照：
 *  - "回执报的却是故障而非零件"（空批次抛异常而非回 0）
 *  - "单有一项撂了挑子，整轮执行说停就停"（一行非法整批作废）
 *  - "办妥的条目下一轮扫描又捞出来重办一过"（同批重复提交重复入库）
 */
class TToponSubmitRowServiceImplTest {

    private TToponSubmitRowMapper mapper;
    private TToponSubmitRowServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TToponSubmitRowMapper.class);
        service = new TToponSubmitRowServiceImpl();
        ReflectionTestUtils.setField(service, "toponSubmitRowMapper", mapper);
    }

    private static TToponSubmitRow row(int rowNo, String itemCode, BigDecimal qty) {
        TToponSubmitRow r = new TToponSubmitRow();
        r.setRowNo(rowNo);
        r.setItemCode(itemCode);
        r.setQty(qty);
        return r;
    }

    /** 空批次返回 0，而不是抛故障 */
    @Test
    void submitBatch_emptyBatch_returnsZero() {
        assertEquals(0, service.submitBatch("B001", Collections.<TToponSubmitRow>emptyList()));
        assertEquals(0, service.submitBatch("B001", null));
        verify(mapper, never()).insert(any());
    }

    /** 超过单批上限（500）返回 -1 且整批不入库 */
    @Test
    void submitBatch_overLimit_returnsMinusOneAndInsertsNothing() {
        List<TToponSubmitRow> rows = new ArrayList<TToponSubmitRow>();
        for (int i = 1; i <= 501; i++) {
            rows.add(row(i, "ITEM" + i, BigDecimal.ONE));
        }
        assertEquals(-1, service.submitBatch("B002", rows), "超上限应回 -1");
        verify(mapper, never()).insert(any());
    }

    /** 一行撂挑子不得拖垮整批：合法行入库、非法行记失败明细、返回成功行数 */
    @Test
    void submitBatch_partialFailure_validRowsStillInserted() {
        when(mapper.selectCount(any())).thenReturn(0);
        List<TToponSubmitRow> rows = Arrays.asList(
                row(1, "A", BigDecimal.ONE),
                row(2, "  ", BigDecimal.ONE),          // 代号空白 → 挂驳回
                row(3, "C", new BigDecimal("-1")),    // 数量非正 → 挂驳回
                row(4, "D", BigDecimal.TEN));
        int ok = service.submitBatch("B003", rows);
        assertEquals(2, ok, "应返回成功行数而非整批作废");

        ArgumentCaptor<TToponSubmitRow> captor = ArgumentCaptor.forClass(TToponSubmitRow.class);
        verify(mapper, times(4)).insert(captor.capture());
        List<TToponSubmitRow> inserted = captor.getAllValues();
        assertEquals(Integer.valueOf(1), inserted.get(0).getStatus(), "合法行应置 已入账(1)");
        assertEquals(Integer.valueOf(2), inserted.get(1).getStatus(), "非法行应置 挂驳回(2)");
        assertEquals(Integer.valueOf(2), inserted.get(2).getStatus());
        assertEquals(Integer.valueOf(1), inserted.get(3).getStatus());
        for (TToponSubmitRow r : inserted) {
            assertEquals("B003", r.getBatchNo(), "应使用入参批次号");
        }
    }

    /** 非法行记入失败明细时保留原始行号 */
    @Test
    void submitBatch_failureRowKeepsOriginalRowNo() {
        when(mapper.selectCount(any())).thenReturn(0);
        service.submitBatch("B004", Collections.singletonList(row(7, null, BigDecimal.ONE)));
        ArgumentCaptor<TToponSubmitRow> captor = ArgumentCaptor.forClass(TToponSubmitRow.class);
        verify(mapper).insert(captor.capture());
        assertEquals(Integer.valueOf(7), captor.getValue().getRowNo(), "失败明细应保留原册内行次");
    }

    /** 同一批次重复提交不得重复入库 */
    @Test
    void submitBatch_duplicateBatch_notInsertedAgain() {
        when(mapper.selectCount(any())).thenReturn(1); // 该批已入账
        int ok = service.submitBatch("B005", Collections.singletonList(row(1, "A", BigDecimal.ONE)));
        assertEquals(0, ok);
        verify(mapper, never()).insert(any());
    }

    /** 失败明细按行号升序 */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listErrors_orderedByRowNo() {
        when(mapper.selectList(any())).thenReturn(new ArrayList<TToponSubmitRow>());
        service.listErrors("B006");
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        String orderSql = ((QueryWrapper<?>) captor.getValue()).getExpression().getOrderBy().getSqlSegment();
        assertTrue(orderSql.contains("row_no"), "失败明细应按行号升序，实际排序段：" + orderSql);
    }
}
