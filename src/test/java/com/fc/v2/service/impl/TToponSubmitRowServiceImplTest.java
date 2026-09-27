package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TToponSubmitRowMapper;
import com.fc.v2.model.auto.TToponSubmitRow;

/**
 * 汇交核收批处理：合法行入账、非法行挂驳、超限整批拒收、重复提交不重入、空批不炸 复现用例。
 */
class TToponSubmitRowServiceImplTest {

    private TToponSubmitRowServiceImpl service;
    private FakeBaseMapper<TToponSubmitRow> mapper;

    @BeforeEach
    void setUp() {
        service = new TToponSubmitRowServiceImpl();
        mapper = new FakeBaseMapper<>();
        Reflect.setField(service, "toponSubmitRowMapper", mapper.as(TToponSubmitRowMapper.class));
    }

    private TToponSubmitRow row(String code, String qty) {
        TToponSubmitRow r = new TToponSubmitRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    private List<TToponSubmitRow> rows(TToponSubmitRow... rs) {
        return new ArrayList<>(Arrays.asList(rs));
    }

    /** 空批次：返回 0，不炸。 */
    @Test
    void emptyBatch_returnsZero() {
        assertEquals(0, service.submitBatch("B0", new ArrayList<>()));
        assertEquals(0, mapper.size());
    }

    /** batchNo 不能空白。 */
    @Test
    void blankBatchNo_rejected() {
        assertEquals(-1, service.submitBatch("  ", rows(row("A", "1"))));
        assertEquals(0, mapper.size());
    }

    /** S12：整批合法全入账，返回成功行数，册号统一落库。 */
    @Test
    void allValid_allInserted() {
        int n = service.submitBatch("B1", rows(row("A", "1"), row("B", "2.5")));
        assertEquals(2, n);
        assertEquals(2, mapper.size());
        for (TToponSubmitRow r : mapper.all()) {
            assertEquals("B1", r.getBatchNo());
            assertEquals(1, r.getStatus().intValue());
        }
    }

    /** S12：一条撂挑子不连累全批——合法行照样入账，非法行挂驳(2)并保留原行号。 */
    @Test
    void mixed_partialSuccess_andErrorsRecorded() {
        TToponSubmitRow badCode = row("  ", "1");
        TToponSubmitRow badQty = row("C", "0");
        TToponSubmitRow nullQty = row("D", null);
        TToponSubmitRow good = row("A", "3");

        int n = service.submitBatch("B2", rows(badCode, badQty, nullQty, good));
        assertEquals(1, n);

        List<TToponSubmitRow> errors = service.listErrors("B2");
        assertEquals(3, errors.size());
        assertEquals(1, errors.get(0).getRowNo().intValue());
        assertEquals(2, errors.get(1).getRowNo().intValue());
        assertEquals(3, errors.get(2).getRowNo().intValue());
        for (TToponSubmitRow e : errors) {
            assertEquals(2, e.getStatus().intValue());
            assertEquals("B2", e.getBatchNo());
        }
        assertEquals(1, mapper.all().stream().filter(r -> r.getStatus() == 1).count());
    }

    /** 失败明细按行号升序。 */
    @Test
    void listErrors_sortedByRowNo() {
        service.submitBatch("B3", rows(row("A", "1"), row(" ", "1"), row("B", "2"), row("C", "-5")));
        List<TToponSubmitRow> errors = service.listErrors("B3");
        assertEquals(Arrays.asList(2, 4), Arrays.asList(errors.get(0).getRowNo(), errors.get(1).getRowNo()));
    }

    /** S6：同册重复提交不得重复入账（幂等）。 */
    @Test
    void duplicateBatch_isIdempotent() {
        assertEquals(2, service.submitBatch("B4", rows(row("A", "1"), row("B", "2"))));
        assertEquals(0, service.submitBatch("B4", rows(row("A", "1"), row("B", "2"))));
        assertEquals(2, mapper.size());
    }

    /** S12：超过单批上限 500 行，整批拒收返回 -1，一条都不得入库。 */
    @Test
    void overLimit_rejectedEntirely() {
        List<TToponSubmitRow> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            big.add(row("C" + i, "1"));
        }
        assertEquals(-1, service.submitBatch("B5", big));
        assertEquals(0, mapper.size());

        // 500 行恰好可以
        List<TToponSubmitRow> edge = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            edge.add(row("E" + i, "1"));
        }
        assertEquals(500, service.submitBatch("B6", edge));
    }
}
