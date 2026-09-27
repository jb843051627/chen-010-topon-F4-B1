package com.fc.v2.topon;

import com.fc.v2.mapper.auto.TToponNameBillMapper;
import com.fc.v2.model.auto.TToponNameBill;
import com.fc.v2.service.impl.TToponNameBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 地名命名更名逐级核准单（approval-chain）复现测试。
 *
 * 线索对照：
 *  - "条目明明动过，状态栏仍蹲在原地"（否决不落"已打回"）
 *  - "归档那头的说法与回退这头的说法两岔，须核齐"（核讫后回退状态不复位）
 *  - "这条复核链两处口径分叉，判据该同根同源"（否决与签批校验不一；会签人数无人记账）
 */
class TToponNameBillServiceImplTest {

    private TToponNameBillMapper mapper;
    private TToponNameBillServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TToponNameBillMapper.class);
        service = new TToponNameBillServiceImpl();
        ReflectionTestUtils.setField(service, "toponNameBillMapper", mapper);
    }

    private TToponNameBill bill(long id, int node, int status, Integer signMode, Integer needCount, Integer signCount) {
        TToponNameBill b = new TToponNameBill();
        b.setId(id);
        b.setBillNo("NB-" + id);
        b.setNodeNo(node);
        b.setStatus(status);
        b.setSignMode(signMode);
        b.setNeedCount(needCount);
        b.setSignCount(signCount);
        b.setDelFlag(0);
        when(mapper.selectById(id)).thenReturn(b);
        when(mapper.updateById(any())).thenReturn(1);
        return b;
    }

    /** 否决必须落"已打回(2)"，不能更新了个寂寞 */
    @Test
    void reject_marksVetoed() {
        bill(1L, 1, 0, 0, 1, 0);
        TToponNameBill r = service.reject(1L, "alice", "依据不足");
        assertNotNull(r);
        assertEquals(Integer.valueOf(2), r.getStatus(), "否决后应置 已打回(2)");
    }

    /** 否决与签批同一把尺：签批人为空同样被拒 */
    @Test
    void reject_blankApprover_returnsNull() {
        bill(1L, 1, 0, 0, 1, 0);
        assertNull(service.reject(1L, "  ", "x"), "签批人为空应与 approve 一样被拒");
    }

    /** 回退与核讫核齐：层数退一，状态复位"在核(0)" */
    @Test
    void rollback_fromPassed_backToRunning() {
        bill(1L, 2, 1, 0, 1, 0); // 已核讫
        TToponNameBill r = service.rollback(1L, "退回补正");
        assertNotNull(r);
        assertEquals(Integer.valueOf(1), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getStatus(), "回退后应复位 在核(0)，不得顶着'已核讫'");
    }

    /** 两名点齐：一票不进层，两票才进层 */
    @Test
    void approve_andMode_needsBothSignatures() {
        TToponNameBill b = bill(1L, 0, 0, 1, 2, 0); // 同层两名点齐
        TToponNameBill r1 = service.approve(1L, "alice", "");
        assertEquals(Integer.valueOf(0), r1.getNodeNo(), "第一票不应进层");
        assertEquals(Integer.valueOf(1), r1.getSignCount(), "第一票应落印计数");
        assertEquals(Integer.valueOf(0), r1.getStatus(), "仍在核");

        TToponNameBill r2 = service.approve(1L, "bob", ""); // b 是同一实例，签数累计
        assertEquals(Integer.valueOf(1), r2.getNodeNo(), "点齐两票才进层");
        assertEquals(Integer.valueOf(0), r2.getSignCount(), "进层后签数清零");
    }

    /** 任一人：一票进层；到顶核讫 */
    @Test
    void approve_orMode_lastNode_passes() {
        bill(1L, 1, 0, 0, 1, 0);
        TToponNameBill r = service.approve(1L, "alice", "");
        assertEquals(Integer.valueOf(2), r.getNodeNo());
        assertEquals(Integer.valueOf(1), r.getStatus(), "到顶应置 已核讫(1)");
    }

    /** 已核讫的单不再受签 */
    @Test
    void approve_alreadyPassed_refused() {
        bill(1L, 2, 1, 0, 1, 0);
        assertNull(service.approve(1L, "alice", ""), "已核讫不得再签");
    }
}
