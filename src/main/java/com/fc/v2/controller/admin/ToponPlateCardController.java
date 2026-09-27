package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TToponPlateCard;
import com.fc.v2.service.ITToponPlateCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 门楼牌号段建档卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "门楼牌号段建档卡")
@Controller
@RequestMapping("/ToponPlateCardController")
public class ToponPlateCardController extends BaseController {

    private final String prefix = "admin/toponPlateCard";

    @Autowired
    private ITToponPlateCardService toponPlateCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("topon:toponPlateCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "门楼牌号段建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("topon:toponPlateCard:list")
    @ResponseBody
    public ResultTable list(TToponPlateCard record) {
        QueryWrapper<TToponPlateCard> queryWrapper = new QueryWrapper<TToponPlateCard>();
        startPage();
        com.github.pagehelper.PageInfo<TToponPlateCard> page =
                new com.github.pagehelper.PageInfo<TToponPlateCard>(toponPlateCardService.selectTToponPlateCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "门楼牌号段建档卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("topon:toponPlateCard:add")
    @ResponseBody
    public AjaxResult add(TToponPlateCard record) {
        return toAjax(toponPlateCardService.insertTToponPlateCard(record));
    }

    @Log(title = "门楼牌号段建档卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("topon:toponPlateCard:edit")
    @ResponseBody
    public AjaxResult editSave(TToponPlateCard record) {
        return toAjax(toponPlateCardService.updateTToponPlateCard(record));
    }

    @Log(title = "门楼牌号段建档卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("topon:toponPlateCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(toponPlateCardService.deleteTToponPlateCardByIds(ids));
    }
}
