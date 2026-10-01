package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TItemCatalogVerMapper;
import com.fc.v2.model.auto.TItemCatalogVer;

/**
 * 版次单流转单测：推进、回退一次都只挪一档，两头同一把尺——
 * 不许隔着档结案，也不许一竿子打回草拟。
 *
 * @author fuce
 * @date 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class TItemCatalogVerServiceImplTest {

    private static final long VER_ID = 7L;

    @Mock
    private TItemCatalogVerMapper itemCatalogVerMapper;

    @InjectMocks
    private TItemCatalogVerServiceImpl service;

    @Test
    void advance_movesExactlyOneStage() {
        when(itemCatalogVerMapper.selectById(VER_ID)).thenReturn(verAt(1));
        TItemCatalogVer r = service.advance(VER_ID, "复核过");
        assertEquals(2, r.getStage().intValue(), "复核之后是付发布，不能隔着档上柜");
    }

    @Test
    void advance_atTopStaysPut() {
        when(itemCatalogVerMapper.selectById(VER_ID)).thenReturn(verAt(3));
        assertEquals(3, service.advance(VER_ID, "到顶").getStage().intValue());
    }

    @Test
    void rollback_movesExactlyOneStageBack() {
        when(itemCatalogVerMapper.selectById(VER_ID)).thenReturn(verAt(2));
        TItemCatalogVer r = service.rollback(VER_ID, "表述不合");
        assertEquals(1, r.getStage().intValue(), "回退一档回到复核，不一竿子打回草拟");
    }

    @Test
    void rollback_atBottomStaysPut() {
        when(itemCatalogVerMapper.selectById(VER_ID)).thenReturn(verAt(0));
        assertEquals(0, service.rollback(VER_ID, "到底").getStage().intValue());
    }

    private TItemCatalogVer verAt(int stage) {
        TItemCatalogVer v = new TItemCatalogVer();
        v.setId(VER_ID);
        v.setStage(stage);
        v.setStatus(1);
        return v;
    }
}
