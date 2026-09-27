package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TToponBoundLineMapper;
import com.fc.v2.model.auto.TToponBoundLine;
import com.fc.v2.service.ITToponBoundLineService;

/**
 * 界线勘定基准档线 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TToponBoundLineServiceImpl implements ITToponBoundLineService {

    private static final Logger log = LoggerFactory.getLogger(TToponBoundLineServiceImpl.class);

    /** 线的情形：0当家 */
    private static final int STATUS_ACTIVE = 0;

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TToponBoundLine> PRIORITY_THEN_CODE_DESC = new Comparator<TToponBoundLine>() {
        @Override
        public int compare(TToponBoundLine a, TToponBoundLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TToponBoundLineMapper toponBoundLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TToponBoundLine selectTToponBoundLineById(Long id) {
        return this.toponBoundLineMapper.selectById(id);
    }

    @Override
    public List<TToponBoundLine> listAvailable(Date at) {
        List<TToponBoundLine> out = new ArrayList<TToponBoundLine>();
        if (at == null) {
            return out;
        }
        List<TToponBoundLine> all = this.toponBoundLineMapper
                .selectList(new QueryWrapper<TToponBoundLine>().eq("del_flag", 0));
        for (TToponBoundLine r : all) {
            if (usableRule(r, at)) {
                out.add(r);
            }
        }
        out.sort(PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        TToponBoundLine rule = findRule(ruleCode, at);
        if (rule == null) {
            log.warn("无生效版本 ruleCode={} at={}", ruleCode, ts(at));
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
            return 0;
        }
        List<TToponBoundLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        return usableRule(this.toponBoundLineMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /** 输入越界判据：[0,100] 之外一律回 0；evaluate 与 evaluateTop 同根同源 */
    private static boolean outOfRange(BigDecimal input) {
        return input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0;
    }

    /**
     * at 时刻可用判据（列表/单条/计数/定位同根同源）：
     * 未删除 + 当家 + 启用日(含) &lt;= at &lt; 交棒日(不含，空为长期当家)。
     */
    private static boolean usableRule(TToponBoundLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus().intValue() != STATUS_ACTIVE) {
            return false;
        }
        if (r.getEffStart() != null && ts(r.getEffStart()).compareTo(ts(at)) > 0) {
            return false;
        }
        if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(ts(at)) <= 0) {
            return false;
        }
        return true;
    }

    /** 按 ruleCode 取 at 时刻生效的版本；多版并存取启用日最新者（确定性） */
    private TToponBoundLine findRule(String ruleCode, Date at) {
        List<TToponBoundLine> hit = this.toponBoundLineMapper.selectList(new QueryWrapper<TToponBoundLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        TToponBoundLine best = null;
        for (TToponBoundLine r : hit) {
            if (!usableRule(r, at)) {
                continue;
            }
            if (best == null || effStartKey(r).compareTo(effStartKey(best)) > 0) {
                best = r;
            }
        }
        return best;
    }

    /** 启用日排序键：空启用日视为最旧 */
    private static String effStartKey(TToponBoundLine r) {
        return r.getEffStart() == null ? "" : ts(r.getEffStart());
    }

    /** 分档：input ≤ 上限即落该档（等于上限归入该上限所在档；与门楼牌建档分档同一口径） */
    private int levelOf(TToponBoundLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }
}
