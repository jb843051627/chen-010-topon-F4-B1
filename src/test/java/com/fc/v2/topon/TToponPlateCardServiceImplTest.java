package com.fc.v2.topon;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponDistrictDocMapper;
import com.fc.v2.mapper.auto.TToponPlateCardMapper;
import com.fc.v2.model.auto.TToponDistrictDoc;
import com.fc.v2.model.auto.TToponPlateCard;
import com.fc.v2.service.impl.TToponPlateCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门楼牌号段建档卡复现测试。
 *
 * 线索对照：
 *  - "起单与改单两个关口卡的尺数不一"（改单不验档案、不重算分档）
 *  - "联动牵扯到的别处也得一并动手，两头的口径要校平"（qty/site 变了，gradeLevel/siteNo 要跟着动）
 *  - "一旦出岔，记录却翻将不出"（物理删除；createBy 被单号污染）
 *  - 列表查询不得私吞调用方条件、不得在服务内强制分页
 */
class TToponPlateCardServiceImplTest {

    private TToponPlateCardMapper baseMapper;
    private TToponDistrictDocMapper docMapper;
    private TToponPlateCardServiceImpl service;

    @BeforeEach
    void setUp() {
        baseMapper = mock(TToponPlateCardMapper.class);
        docMapper = mock(TToponDistrictDocMapper.class);
        service = new TToponPlateCardServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "toponDistrictDocMapper", docMapper);
    }

    private static TToponDistrictDoc doc(int id, String siteNo, int status) {
        TToponDistrictDoc d = new TToponDistrictDoc();
        d.setId((long) id);
        d.setSiteNo(siteNo);
        d.setStatus(status);
        d.setDelFlag(0);
        d.setTh1Max(new BigDecimal("40"));
        d.setTh2Max(new BigDecimal("200"));
        d.setTh3Max(new BigDecimal("600"));
        return d;
    }

    /** 列表：调用方的条件与分页由调用方说了算，服务不得另起炉灶 */
    @Test
    @SuppressWarnings("unchecked")
    void list_passesCallerWrapperThrough() {
        QueryWrapper<TToponPlateCard> w = new QueryWrapper<TToponPlateCard>()
                .eq("del_flag", 0).eq("status", 1);
        when(baseMapper.selectList(any())).thenReturn(new ArrayList<TToponPlateCard>());
        service.selectTToponPlateCardList(w);
        verify(baseMapper).selectList(same(w));
    }

    /** 起单：档案联动（区划回填 + 分档），createBy 不得被单号污染 */
    @Test
    void insert_linksArchiveAndKeepsCreateByClean() {
        TToponDistrictDoc d = doc(0, "XZ00", 0);
        when(docMapper.selectOne(any())).thenReturn(d);
        when(docMapper.selectById(0)).thenReturn(d);
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);

        TToponPlateCard record = new TToponPlateCard();
        record.setBillNo("P1");
        record.setSiteId(0);
        record.setQty(new BigDecimal("150")); // 40 < 150 <= 200 → 二档

        assertEquals(1, service.insertTToponPlateCard(record));
        assertEquals("XZ00", record.getSiteNo(), "应回填档案区划代码");
        assertEquals(Integer.valueOf(2), record.getGradeLevel(), "应按档案上限分档");
        assertNull(record.getCreateBy(), "创建者不得被单号污染（交给自动填充）");
    }

    /** 起单：已撤并档案拒单 */
    @Test
    void insert_withdrawnArchive_refused() {
        when(docMapper.selectOne(any())).thenReturn(doc(1, "XZ01", 1));
        TToponPlateCard record = new TToponPlateCard();
        record.setBillNo("P2");
        record.setSiteId(1);
        record.setQty(BigDecimal.ONE);
        assertEquals(0, service.insertTToponPlateCard(record));
        verify(baseMapper, never()).insert(any());
    }

    /** 改单与起单同一把尺：qty 变了，分档跟着重算 */
    @Test
    void update_recomputesGradeLikeInsert() {
        TToponPlateCard db = new TToponPlateCard();
        db.setId(9L);
        db.setBillNo("P9");
        db.setSiteId(0);
        db.setSiteNo("XZ00");
        db.setQty(new BigDecimal("30"));
        db.setGradeLevel(1);
        db.setDelFlag(0);
        when(baseMapper.selectById(9L)).thenReturn(db);
        when(docMapper.selectOne(any())).thenReturn(doc(0, "XZ00", 0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.update(any(), any())).thenReturn(1);

        TToponPlateCard record = new TToponPlateCard();
        record.setId(9L);
        record.setQty(new BigDecimal("250")); // 200 < 250 <= 600 → 三档

        assertEquals(1, service.updateTToponPlateCard(record));
        assertEquals(Integer.valueOf(3), record.getGradeLevel(), "qty 变了分档要联动重算");
        assertEquals("XZ00", record.getSiteNo(), "区划代码要随档案校平");
    }

    /** 改单与起单同一把尺：档案已撤并同样拒单 */
    @Test
    void update_withdrawnArchive_refusedLikeInsert() {
        TToponPlateCard db = new TToponPlateCard();
        db.setId(9L);
        db.setSiteId(1);
        db.setDelFlag(0);
        when(baseMapper.selectById(9L)).thenReturn(db);
        when(docMapper.selectOne(any())).thenReturn(doc(1, "XZ01", 1)); // 已撤并
        when(baseMapper.selectCount(any())).thenReturn(0);

        TToponPlateCard record = new TToponPlateCard();
        record.setId(9L);
        record.setQty(new BigDecimal("100"));

        assertEquals(0, service.updateTToponPlateCard(record), "改单碰到已撤并档案应与起单一样拒单");
        verify(baseMapper, never()).update(any(), any());
    }

    /** 删除改逻辑删除：记录翻得出来 */
    @Test
    void delete_marksDelFlagInsteadOfPhysicalDelete() {
        when(baseMapper.update(any(), any())).thenReturn(1);
        service.deleteTToponPlateCardById(9L);
        ArgumentCaptor<TToponPlateCard> captor = ArgumentCaptor.forClass(TToponPlateCard.class);
        verify(baseMapper).update(captor.capture(), any());
        assertEquals(Integer.valueOf(1), captor.getValue().getDelFlag(), "应置删除标记而非物理删除");
        verify(baseMapper, never()).deleteById(any(Long.class));
    }
}
