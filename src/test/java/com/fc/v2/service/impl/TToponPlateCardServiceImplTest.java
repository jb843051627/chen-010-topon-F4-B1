package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponDistrictDocMapper;
import com.fc.v2.mapper.auto.TToponPlateCardMapper;
import com.fc.v2.model.auto.TToponDistrictDoc;
import com.fc.v2.model.auto.TToponPlateCard;

/**
 * 门楼牌号段建档卡：起单/改单同尺同寸、容量档与档线同源、软删 复现用例。
 */
class TToponPlateCardServiceImplTest {

    private TToponPlateCardServiceImpl service;
    private FakeBaseMapper<TToponPlateCard> cardMapper;
    private FakeBaseMapper<TToponDistrictDoc> docMapper;

    @BeforeEach
    void setUp() {
        service = new TToponPlateCardServiceImpl();
        cardMapper = new FakeBaseMapper<>();
        docMapper = new FakeBaseMapper<>();
        Reflect.setField(service, "baseMapper", cardMapper.as(TToponPlateCardMapper.class));
        Reflect.setField(service, "toponDistrictDocMapper", docMapper.as(TToponDistrictDocMapper.class));

        TToponDistrictDoc alive = new TToponDistrictDoc();
        alive.setId(0L);
        alive.setSiteNo("XZ00");
        alive.setStatus(0);
        alive.setDelFlag(0);
        alive.setTh1Max(new BigDecimal("40"));
        alive.setTh2Max(new BigDecimal("200"));
        alive.setTh3Max(new BigDecimal("600"));
        docMapper.seed(alive);

        TToponDistrictDoc merged = new TToponDistrictDoc();
        merged.setId(1L);
        merged.setSiteNo("XZ01");
        merged.setStatus(1);
        merged.setDelFlag(0);
        merged.setTh1Max(new BigDecimal("40"));
        merged.setTh2Max(new BigDecimal("200"));
        merged.setTh3Max(new BigDecimal("600"));
        docMapper.seed(merged);
    }

    private TToponPlateCard card(long id, String billNo, Integer siteId, String qty, Integer status, Integer delFlag) {
        TToponPlateCard r = new TToponPlateCard();
        r.setId(id);
        r.setBillNo(billNo);
        r.setSiteId(siteId);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    private TToponPlateCard newCard(String billNo, int siteId, String qty) {
        TToponPlateCard r = new TToponPlateCard();
        r.setBillNo(billNo);
        r.setSiteId(siteId);
        r.setQty(new BigDecimal(qty));
        return r;
    }

    /** 起单：档案区划号回填、容量档按 40/200/600 分档（压线留本档）。 */
    @Test
    void insert_happy_gradesAndCopiesSiteNo() {
        assertEquals(1, service.insertTToponPlateCard(newCard("P1", 0, "40")));
        assertEquals(1, cardMapper.all().get(0).getGradeLevel().intValue());
        assertEquals("XZ00", cardMapper.all().get(0).getSiteNo());
        assertEquals(0, cardMapper.all().get(0).getDelFlag().intValue());

        assertEquals(1, service.insertTToponPlateCard(newCard("P2", 0, "40.01")));
        assertEquals(2, cardMapper.stored(cardMapper.all().get(1).getId()).getGradeLevel().intValue());
        assertEquals(1, service.insertTToponPlateCard(newCard("P3", 0, "200")));
        assertEquals(1, service.insertTToponPlateCard(newCard("P4", 0, "600")));
        assertEquals(1, service.insertTToponPlateCard(newCard("P5", 0, "601")));
        assertEquals(3, cardMapper.stored(cardMapper.all().get(3).getId()).getGradeLevel().intValue());
        assertEquals(4, cardMapper.stored(cardMapper.all().get(4).getId()).getGradeLevel().intValue());
    }

    /** 起单：档案不存在 / 已撤并 一律拒收。 */
    @Test
    void insert_badSite_rejected() {
        assertEquals(0, service.insertTToponPlateCard(newCard("P1", 1, "10")));
        assertEquals(0, service.insertTToponPlateCard(newCard("P2", 99, "10")));
        assertEquals(0, cardMapper.size());
    }

    /** 起单：同号不得重复建档。 */
    @Test
    void insert_duplicateBillNo_rejected() {
        assertEquals(1, service.insertTToponPlateCard(newCard("P1", 0, "10")));
        assertEquals(0, service.insertTToponPlateCard(newCard("P1", 0, "10")));
        assertEquals(1, cardMapper.size());
    }

    /** S4：改单与起单同尺同寸——改挂到已撤并档案同样得拒收。 */
    @Test
    void update_validatesSite_likeInsert() {
        cardMapper.seed(card(10, "P1", 0, "10", 0, 0));
        TToponPlateCard r = card(10, "P1", 1, "10", 0, 0);
        assertEquals(0, service.updateTToponPlateCard(r));
        assertEquals(Integer.valueOf(0), cardMapper.stored(10L).getSiteId());
    }

    /** S4+S10：改单改了数量，容量档必须重算，不能改一半留一半。 */
    @Test
    void update_recomputesGrade() {
        cardMapper.seed(card(10, "P1", 0, "40", 0, 0));
        cardMapper.stored(10L).setGradeLevel(1);
        TToponPlateCard r = card(10, "P1", 0, "300", 0, 0);
        assertEquals(1, service.updateTToponPlateCard(r));
        assertEquals(new BigDecimal("300"), cardMapper.stored(10L).getQty());
        assertEquals(3, cardMapper.stored(10L).getGradeLevel().intValue());
        assertNotNull(cardMapper.stored(10L).getUpdateTime());
    }

    /** 改单：撞别人的在档卡号仍拒收。 */
    @Test
    void update_duplicateBillNo_rejected() {
        cardMapper.seed(card(10, "P1", 0, "10", 0, 0));
        cardMapper.seed(card(11, "P2", 0, "10", 0, 0));
        TToponPlateCard r = card(11, "P1", 0, "10", 0, 0);
        assertEquals(0, service.updateTToponPlateCard(r));
    }

    /** 已软删的卡改不得。 */
    @Test
    void update_deletedRow_rejected() {
        cardMapper.seed(card(10, "P1", 0, "10", 0, 1));
        assertEquals(0, service.updateTToponPlateCard(card(10, "P1", 0, "11", 0, 1)));
    }

    /** S4：删除是软删，账留痕；删后查不到、卡号可重新起用。 */
    @Test
    void delete_isSoft_andReusable() {
        cardMapper.seed(card(10, "P1", 0, "10", 0, 0));
        assertEquals(1, service.deleteTToponPlateCardById(10L));
        assertEquals(Integer.valueOf(1), cardMapper.stored(10L).getDelFlag());
        assertNull(service.selectTToponPlateCardById(10L));

        assertEquals(1, service.insertTToponPlateCard(newCard("P1", 0, "10")));
    }

    /** 列表：尊重调用方条件，且软删的一律不列出，不硬绑 status=0。 */
    @Test
    void list_honorsWrapper_excludesDeleted() {
        cardMapper.seed(card(10, "P1", 0, "10", 0, 0));
        cardMapper.seed(card(11, "P2", 0, "10", 1, 0));
        cardMapper.seed(card(12, "P3", 0, "10", 2, 1));

        List<TToponPlateCard> all = service.selectTToponPlateCardList(new QueryWrapper<>());
        assertEquals(2, all.size());

        List<TToponPlateCard> onlyDone = service.selectTToponPlateCardList(
                new QueryWrapper<TToponPlateCard>().eq("status", 1));
        assertEquals(1, onlyDone.size());
        assertEquals("P2", onlyDone.get(0).getBillNo());
    }
}
