package com.fc.v2.topon;

import com.fc.v2.mapper.auto.TToponMarkerCardMapper;
import com.fc.v2.model.auto.TToponMarkerCard;
import com.fc.v2.service.impl.TToponMarkerCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 界桩标志补办事务卡（state-machine）复现测试。
 *
 * 线索对照：
 *  - "条目明明动过，状态栏仍蹲在原地"（到顶不落"已封存"）
 *  - "归档那头的说法与回退这头的说法两岔，须核齐"（推进 +2 格、回退直接归零）
 *  - "一旦出岔，记录却翻将不出"（物理删除）；已办结不得再改/再删
 */
class TToponMarkerCardServiceImplTest {

    private TToponMarkerCardMapper mapper;
    private TToponMarkerCardServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TToponMarkerCardMapper.class);
        service = new TToponMarkerCardServiceImpl();
        ReflectionTestUtils.setField(service, "toponMarkerCardMapper", mapper);
    }

    private TToponMarkerCard card(long id, int stage, int status) {
        TToponMarkerCard c = new TToponMarkerCard();
        c.setId(id);
        c.setBizNo("BZ-" + id);
        c.setStage(stage);
        c.setStatus(status);
        c.setDelFlag(0);
        when(mapper.selectById(id)).thenReturn(c);
        when(mapper.updateById(any())).thenReturn(1);
        return c;
    }

    /** 推进一档：一次挪一格，不得跨格 */
    @Test
    void advance_movesOneStage() {
        card(1L, 0, 1);
        TToponMarkerCard r = service.advance(1L, "挪格");
        assertNotNull(r);
        assertEquals(Integer.valueOf(1), r.getStage(), "推进一档应 +1，不得 +2");
    }

    /** 挪到顶格：落定"已封存(2)" */
    @Test
    void advance_toMaxStage_marksTerminal() {
        card(1L, 2, 1);
        TToponMarkerCard r = service.advance(1L, "x");
        assertEquals(Integer.valueOf(3), r.getStage());
        assertEquals(Integer.valueOf(2), r.getStatus(), "到顶应置 已封存(2)，状态栏不得蹲在原地");
    }

    /** 已封存不得再推进 */
    @Test
    void advance_terminalCard_refused() {
        card(1L, 3, 2);
        assertNull(service.advance(1L, "x"), "已封存（已办结）不得再推进");
    }

    /** 回退一档：一次退一格，不得直接归零 */
    @Test
    void rollback_movesOneStageBack() {
        card(1L, 2, 1);
        TToponMarkerCard r = service.rollback(1L, "x");
        assertNotNull(r);
        assertEquals(Integer.valueOf(1), r.getStage(), "回退一档应 -1，不得一脚踹回 0");
    }

    /** 归档那头与回退这头核齐：封存卡回退后回到"在办" */
    @Test
    void rollback_fromTerminal_backToActive() {
        card(1L, 3, 2);
        TToponMarkerCard r = service.rollback(1L, "x");
        assertNotNull(r);
        assertEquals(Integer.valueOf(2), r.getStage());
        assertEquals(Integer.valueOf(1), r.getStatus(), "从封存回退应复位到在办(1)");
    }

    /** 已办结不得再改 */
    @Test
    void updateContent_terminalCard_refused() {
        card(1L, 3, 2);
        assertFalse(service.updateContent(1L, "x"), "已办结不得再改");
    }

    /** 已办结不得删除；删除为逻辑删除（记录翻得出来） */
    @Test
    void remove_terminalRefused_andLogicalDelete() {
        card(1L, 3, 2);
        assertFalse(service.remove(1L), "已办结不得删除");

        card(2L, 1, 1);
        assertTrue(service.remove(2L));
        ArgumentCaptor<TToponMarkerCard> captor = ArgumentCaptor.forClass(TToponMarkerCard.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getDelFlag(), "应置删除标记而非物理删除");
        verify(mapper, never()).deleteById(any(Long.class));
    }
}
