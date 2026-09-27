package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponMarkerCardMapper;
import com.fc.v2.model.auto.TToponMarkerCard;

/**
 * 事务卡状态机：推进/回退只动一格、状态栏跟着格口走、办结封存不得擅动 复现用例。
 */
class TToponMarkerCardServiceImplTest {

    private TToponMarkerCardServiceImpl service;
    private FakeBaseMapper<TToponMarkerCard> mapper;

    @BeforeEach
    void setUp() {
        service = new TToponMarkerCardServiceImpl();
        mapper = new FakeBaseMapper<>();
        Reflect.setField(service, "toponMarkerCardMapper", mapper.as(TToponMarkerCardMapper.class));
    }

    private TToponMarkerCard card(long id, Integer stage, Integer status, Integer delFlag) {
        TToponMarkerCard r = new TToponMarkerCard();
        r.setId(id);
        r.setBizNo("M" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    /** S3：推进只走一格，不是蹦两格。 */
    @Test
    void advance_movesOneStage() {
        mapper.seed(card(1, 0, 0, 0));
        assertEquals(1, service.advance(1L, "送审").getStage().intValue());
    }

    /** S3：条目动过，状态栏不能蹲在原地——在途推进仍是"在办"(0)。 */
    @Test
    void advance_midStaysActive_zero() {
        mapper.seed(card(1, 1, 0, 0));
        TToponMarkerCard r = service.advance(1L, "复核");
        assertEquals(2, r.getStage().intValue());
        assertEquals(0, r.getStatus().intValue());
        assertEquals("复核", r.getLastAction());
    }

    /** 走到顶格才成卷(status=1)。 */
    @Test
    void advance_toLastStage_closes() {
        mapper.seed(card(1, 2, 0, 0));
        TToponMarkerCard r = service.advance(1L, "归档");
        assertEquals(3, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    /** 已到顶继续推进：拒收，不再跳。 */
    @Test
    void advance_atTop_rejected() {
        mapper.seed(card(1, 3, 1, 0));
        assertNull(service.advance(1L, "再来"));
        assertEquals(3, mapper.stored(1L).getStage().intValue());
    }

    /** 已封存(2)的卡不得推进。 */
    @Test
    void advance_terminal_rejected() {
        mapper.seed(card(1, 3, 2, 0));
        assertNull(service.advance(1L, "x"));
    }

    /** S7：回退只退一格，不是一把归零。 */
    @Test
    void rollback_movesOneStage() {
        mapper.seed(card(1, 2, 0, 0));
        TToponMarkerCard r = service.rollback(1L, "补料");
        assertEquals(1, r.getStage().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    /** 已在第 0 格，无处可退：拒收。 */
    @Test
    void rollback_atZero_rejected() {
        mapper.seed(card(1, 0, 0, 0));
        assertNull(service.rollback(1L, "x"));
    }

    /** 已成卷的卡退回：回到上一格、重新在办；已封存不得退。 */
    @Test
    void rollback_closed_reopens_terminalRejected() {
        mapper.seed(card(1, 3, 1, 0));
        TToponMarkerCard r = service.rollback(1L, "重勘");
        assertEquals(2, r.getStage().intValue());
        assertEquals(0, r.getStatus().intValue());

        mapper.seed(card(2, 3, 2, 0));
        assertNull(service.rollback(2L, "x"));
    }

    /** 已办结（成卷/封存）不得再改内容、不得删除。 */
    @Test
    void closedAndTerminal_immutable() {
        mapper.seed(card(1, 3, 1, 0));
        mapper.seed(card(2, 3, 2, 0));
        assertFalse(service.updateContent(1L, "篡改"));
        assertFalse(service.remove(1L));
        assertFalse(service.updateContent(2L, "篡改"));
        assertFalse(service.remove(2L));
    }

    /** 在办卡可改可删（软删），台账不再翻出已删卡。 */
    @Test
    void active_editableAndSoftDeleted() {
        mapper.seed(card(1, 0, 0, 0));
        assertTrue(service.updateContent(1L, "补记事"));
        assertEquals("补记事", mapper.stored(1L).getContent());

        assertTrue(service.remove(1L));
        assertEquals(Integer.valueOf(1), mapper.stored(1L).getDelFlag());
        List<TToponMarkerCard> list = service.selectTToponMarkerCardList(new QueryWrapper<>());
        assertEquals(0, list.size());
    }

    /** 流转台账尊重调用方条件，但已软删的卡一律不得翻出。 */
    @Test
    void listLedger_excludesSoftDeleted() {
        mapper.seed(card(1, 0, 0, 0));
        mapper.seed(card(2, 3, 1, 0));
        mapper.seed(card(3, 1, 0, 1));
        List<TToponMarkerCard> list = service.selectTToponMarkerCardList(new QueryWrapper<>());
        assertEquals(2, list.size());
    }
}
