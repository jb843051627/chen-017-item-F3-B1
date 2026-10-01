package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemCatalogVerMapper;
import com.fc.v2.model.auto.TItemCatalogVer;
import com.fc.v2.service.ITItemCatalogVerService;

/**
 * 事项目录条目版次单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemCatalogVerServiceImpl implements ITItemCatalogVerService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TItemCatalogVerMapper itemCatalogVerMapper;

    @Override
    public TItemCatalogVer selectTItemCatalogVerById(Long id) {
        return this.itemCatalogVerMapper.selectById(id);
    }

    @Override
    public List<TItemCatalogVer> selectTItemCatalogVerList(QueryWrapper<TItemCatalogVer> queryWrapper) {
        return this.itemCatalogVerMapper.selectList(queryWrapper);
    }

    @Override
    public TItemCatalogVer advance(Long id, String remark) {
        TItemCatalogVer r = this.itemCatalogVerMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 推进一档就是一档：草拟→复核→付发布→已上柜，不许隔着档结案。
        r.setStage(Math.min(st + 1, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.itemCatalogVerMapper.updateById(r);
        return r;
    }

    @Override
    public TItemCatalogVer rollback(Long id, String remark) {
        TItemCatalogVer r = this.itemCatalogVerMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 回退与推进同一把尺：一次也只挪一档，不一竿子打回草拟。
        r.setStage(Math.max(st - 1, 0));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.itemCatalogVerMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TItemCatalogVer r = this.itemCatalogVerMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.itemCatalogVerMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TItemCatalogVer r = this.itemCatalogVerMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.itemCatalogVerMapper.deleteById(id) > 0;
    }

}
