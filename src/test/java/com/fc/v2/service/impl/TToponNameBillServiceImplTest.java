package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TToponNameBillMapper;
import com.fc.v2.model.auto.TToponNameBill;

/**
 * 命名更名核准单：签批/打回/退回 三向状态口径 复现用例。
 */
class TToponNameBillServiceImplTest {

    private TToponNameBillServiceImpl service;
    private FakeBaseMapper<TToponNameBill> mapper;

    @BeforeEach
    void setUp() {
        service = new TToponNameBillServiceImpl();
        mapper = new FakeBaseMapper<>();
        Reflect.setField(service, "toponNameBillMapper", mapper.as(TToponNameBillMapper.class));
    }

    private TToponNameBill bill(long id, int node, int status) {
        TToponNameBill r = new TToponNameBill();
        r.setId(id);
        r.setBillNo("B" + id);
        r.setNodeNo(node);
        r.setStatus(status);
        r.setSignMode(0);
        r.setNeedCount(1);
        r.setSignCount(0);
        return r;
    }

    /** 首次签批：进一层，仍在核。 */
    @Test
    void approve_advancesNode_staysRunning() {
        mapper.seed(bill(1, 0, 0));
        TToponNameBill r = service.approve(1L, "zhang", "可");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getStatus().intValue());
        assertEquals("zhang", r.getUpdateBy());
    }

    /** 末层签批：核讫归档。 */
    @Test
    void approve_finalNode_passes() {
        mapper.seed(bill(1, 1, 0));
        TToponNameBill r = service.approve(1L, "zhang", "可");
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    /** S7：驳回必须落成"已打回"(2)，不能原样蹲在"在核"。 */
    @Test
    void reject_marksVeto() {
        mapper.seed(bill(1, 1, 0));
        TToponNameBill r = service.reject(1L, "li", "名实不符");
        assertNotNull(r);
        assertEquals(2, r.getStatus().intValue());
        assertEquals("li", r.getUpdateBy());
    }

    /** 签批/驳回两道门同规同约：批准人为空一律拒收。 */
    @Test
    void approveAndReject_blankApprover_rejected() {
        mapper.seed(bill(1, 0, 0));
        assertNull(service.approve(1L, "  ", "x"));
        assertNull(service.reject(1L, null, "x"));
        assertEquals(0, mapper.stored(1L).getStatus().intValue());
    }

    /** 已打回的单不得再签批。 */
    @Test
    void approve_afterVeto_rejected() {
        mapper.seed(bill(1, 1, 2));
        assertNull(service.approve(1L, "zhang", "可"));
    }

    /** S7：打回后再退回，层数减一、情形回在核——归档与回退口径核齐。 */
    @Test
    void rollback_afterVeto_restartsRunning() {
        mapper.seed(bill(1, 1, 2));
        TToponNameBill r = service.rollback(1L, "重来");
        assertEquals(0, r.getNodeNo().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    /** 在核中退回：只退一层，不是原地不动。 */
    @Test
    void rollback_running_decrementsOneNode() {
        mapper.seed(bill(1, 2, 0));
        assertEquals(1, service.rollback(1L, "x").getNodeNo().intValue());
    }

    /** 已核讫归档的单不得退回。 */
    @Test
    void rollback_passed_rejected() {
        mapper.seed(bill(1, 2, 1));
        assertNull(service.rollback(1L, "x"));
        assertEquals(2, mapper.stored(1L).getNodeNo().intValue());
        assertEquals(1, mapper.stored(1L).getStatus().intValue());
    }
}
