package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TToponMarkerCard;
import com.fc.v2.service.ITToponMarkerCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 界桩标志补办事务卡 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "界桩标志补办事务卡")
@Controller
@RequestMapping("/toponMarkerCard")
public class ToponMarkerCardController extends BaseController {

    private final String prefix = "admin/toponMarkerCard";

    @Autowired
    private ITToponMarkerCardService toponMarkerCardService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("toponMarkerCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "界桩标志补办事务卡流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("toponMarkerCard:list")
    @ResponseBody
    public ResultTable list(TToponMarkerCard record) {
        QueryWrapper<TToponMarkerCard> queryWrapper = new QueryWrapper<TToponMarkerCard>();
        startPage();
        com.github.pagehelper.PageInfo<TToponMarkerCard> page =
                new com.github.pagehelper.PageInfo<TToponMarkerCard>(toponMarkerCardService.selectTToponMarkerCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "界桩标志补办事务卡推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("toponMarkerCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(toponMarkerCardService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "界桩标志补办事务卡回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("toponMarkerCard:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(toponMarkerCardService.rollback(id, remark) != null ? 1 : 0);
    }
}
