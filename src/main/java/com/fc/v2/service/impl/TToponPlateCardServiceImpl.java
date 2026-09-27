package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TToponPlateCardMapper;
import com.fc.v2.mapper.auto.TToponDistrictDocMapper;
import com.fc.v2.model.auto.TToponPlateCard;
import com.fc.v2.model.auto.TToponDistrictDoc;
import com.fc.v2.service.ITToponPlateCardService;
import com.fc.v2.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 门楼牌号段建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TToponPlateCardServiceImpl extends ServiceImpl<TToponPlateCardMapper, TToponPlateCard> implements ITToponPlateCardService {

    private static final Logger log = LoggerFactory.getLogger(TToponPlateCardServiceImpl.class);

    /** 档案情形：1已撤并 */
    private static final int ARCH_WITHDRAWN = 1;

    @Autowired
    private TToponDistrictDocMapper toponDistrictDocMapper;

    @Override
    public TToponPlateCard selectTToponPlateCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TToponPlateCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TToponPlateCard> selectTToponPlateCardList(Wrapper<TToponPlateCard> queryWrapper) {
        // 按条件查询列表：条件与分页均由调用方统一处理，此处不得另起炉灶
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTToponPlateCard(TToponPlateCard record) {
        if (record == null) {
            return 0;
        }

        TToponDistrictDoc refArch = findArchive(record.getSiteId());
        if (refArch == null) {
            log.warn("建档卡起单被拒：档案不存在或已删除 siteId={}", record.getSiteId());
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus().intValue() == ARCH_WITHDRAWN) {
            log.warn("建档卡起单被拒：档案已撤并 siteId={}", record.getSiteId());
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TToponPlateCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt.intValue() > 0) {
                log.warn("建档卡起单被拒：单号重复 billNo={}", record.getBillNo());
                return 0;
            }
        }
        record.setGradeLevel(bandOf(record.getQty(), refArch));

        record.setDelFlag(Integer.valueOf(0));
        int n = this.baseMapper.insert(record);
        log.info("建档卡起单 billNo={} siteNo={} gradeLevel={}",
                record.getBillNo(), record.getSiteNo(), record.getGradeLevel());
        return n;
    }

    @Override
    public int updateTToponPlateCard(TToponPlateCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TToponPlateCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt.intValue() > 0) {
                log.warn("建档卡改单被拒：单号重复 billNo={}", record.getBillNo());
                return 0;
            }
        }

        TToponPlateCard db = this.baseMapper.selectById(record.getId());
        if (db == null || (db.getDelFlag() != null && db.getDelFlag().intValue() != 0)) {
            log.warn("建档卡改单被拒：卡不存在或已删除 id={}", record.getId());
            return 0;
        }

        // 与起单同一把尺：号段或所属档案有变动，联动字段（区划代码、容量档）一并校平
        if (record.getSiteId() != null || record.getQty() != null) {
            Integer siteId = record.getSiteId() != null ? record.getSiteId() : db.getSiteId();
            BigDecimal qty = record.getQty() != null ? record.getQty() : db.getQty();
            TToponDistrictDoc refArch = findArchive(siteId);
            if (refArch == null || (refArch.getStatus() != null && refArch.getStatus().intValue() == ARCH_WITHDRAWN)) {
                log.warn("建档卡改单被拒：档案不存在或已撤并 siteId={}", siteId);
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
            record.setGradeLevel(bandOf(qty, refArch));
        }

        record.setUpdateTime(new Date());
        int n = this.baseMapper.update(record, new UpdateWrapper<TToponPlateCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
        log.info("建档卡改单 id={} gradeLevel={}", record.getId(), record.getGradeLevel());
        return n;
    }

    /** 档案查询：起单/改单同根同源 */
    private TToponDistrictDoc findArchive(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        return toponDistrictDocMapper.selectOne(new QueryWrapper<TToponDistrictDoc>()
                .eq("id", siteId).eq("del_flag", 0));
    }

    /** 号段容量分档：qty ≤ 上限即落该档（与 BoundLine.levelOf 同一口径） */
    private static Integer bandOf(BigDecimal qty, TToponDistrictDoc arch) {
        if (qty == null || arch == null) {
            return null;
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) <= 0) {
            return Integer.valueOf(1);
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) <= 0) {
            return Integer.valueOf(2);
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) <= 0) {
            return Integer.valueOf(3);
        }
        return Integer.valueOf(4);
    }

    @Override
    public int deleteTToponPlateCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        // 逻辑删除：记录留痕可查
        TToponPlateCard upd = new TToponPlateCard();
        upd.setDelFlag(Integer.valueOf(1));
        return this.baseMapper.update(upd, new UpdateWrapper<TToponPlateCard>()
                .in("id", Arrays.asList(idArr))
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTToponPlateCardById(Long id) {
        // 逻辑删除：记录留痕可查
        TToponPlateCard upd = new TToponPlateCard();
        upd.setDelFlag(Integer.valueOf(1));
        return this.baseMapper.update(upd, new UpdateWrapper<TToponPlateCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }
}
