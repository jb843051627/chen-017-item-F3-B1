package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemChangeBillMapper;
import com.fc.v2.mapper.auto.TItemChangeBillSignMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.auto.TItemChangeBillSign;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.model.custom.ChangeBillCreateReq;
import com.fc.v2.model.custom.ChangeBillTraceVo;

/**
 * 三道口规矩单测：不落库，签迹由 mock 点回来——
 * 落了几个名只看签迹给回什么，屏上摆的数、心里记的数都不作数。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
class TItemChangeBillServiceImplTest {

    private static final long BILL_ID = 1001L;
    private static final String ITEM_CODE = "XZQR-007";

    @Mock
    private TItemChangeBillMapper itemChangeBillMapper;
    @Mock
    private TItemChangeBillSignMapper itemChangeBillSignMapper;
    @Mock
    private TItemDeptArchMapper itemDeptArchMapper;
    @Mock
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @InjectMocks
    private TItemChangeBillServiceImpl service;

    // ---------- 县核：收材料、前置检 ----------

    @Test
    void create_missingAnyOfFourElements_billCannotStart() {
        String[] valid = {ITEM_CODE, "旧承办口径", "新承办口径", "去年八月补录对不上"};
        for (int missing = 0; missing < valid.length; missing++) {
            ChangeBillCreateReq req = new ChangeBillCreateReq();
            req.setItemCode(missing == 0 ? " " : valid[0]);
            req.setBeforeFields(missing == 1 ? null : valid[1]);
            req.setAfterFields(missing == 2 ? "" : valid[2]);
            req.setChangeReason(missing == 3 ? "   " : valid[3]);
            req.setSiteNo("ZW00");

            assertNull(service.createBill(req), "第" + missing + "样缺了件不该起来");
        }
        verify(itemChangeBillMapper, never()).insert(any());
    }

    @Test
    void create_archMissing_precheckFailsAndBillStuckAtCounty() {
        when(itemDeptArchMapper.selectOne(any())).thenReturn(null);
        when(itemChangeBillMapper.selectCount(any())).thenReturn(0);

        TItemChangeBill bill = service.createBill(req("ZW99"));
        assertNotNull(bill);
        assertEquals(0, bill.getNodeNo().intValue());
        assertEquals(0, bill.getArchCheck().intValue(), "底册查无此处，前置检不过");
    }

    @Test
    void approve_precheckFailedBill_neverMovesEvenIfMaterialsCompletedLater() {
        // 承办底册没过前置那道检的，件停在县核，往后再齐也不往前挪。
        TItemChangeBill stuck = runningBill(0, 1);
        stuck.setArchCheck(0);
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(stuck);

        assertNull(service.approve(BILL_ID, "县员甲", "材料后来补齐了"));
        verify(itemChangeBillSignMapper, never()).insert(any());
        verify(itemChangeBillMapper, never()).updateById(any());
    }

    @Test
    void create_archDismissed_statusOneFailsPrecheck() {
        TItemDeptArch dismissed = new TItemDeptArch();
        dismissed.setSiteNo("ZW01");
        dismissed.setStatus(1);
        when(itemDeptArchMapper.selectOne(any())).thenReturn(dismissed);
        when(itemChangeBillMapper.selectCount(any())).thenReturn(0);

        TItemChangeBill bill = service.createBill(req("ZW01"));
        assertEquals(0, bill.getArchCheck().intValue());
        assertEquals(0, bill.getNodeNo().intValue());
    }

    @Test
    void create_secondBillOfSameItem_seqCalledOnTheSpot() {
        when(itemDeptArchMapper.selectOne(any())).thenReturn(archIn编());
        when(itemChangeBillMapper.selectCount(any())).thenReturn(1);

        TItemChangeBill second = service.createBill(req("ZW00"));
        assertEquals(2, second.getSeqNo().intValue());
        assertEquals(1, second.getRoundNo().intValue());
        assertTrue(second.getBillNo().endsWith("-R1-2"));
    }

    // ---------- 口次：按口次走，不按催得急走 ----------

    @Test
    void approve_countyMovesExactlyOneNodeToCity() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(0, 1));
        when(itemChangeBillSignMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(itemChangeBillSignMapper.selectCount(any())).thenReturn(1);

        TItemChangeBill r = service.approve(BILL_ID, "县员甲", "县核收讫");
        assertEquals(1, r.getNodeNo().intValue(), "县核过了只到市核，不能跳到省定");
        assertEquals(0, r.getSignCount().intValue(), "新口重新点数");
        assertEquals(1, r.getNeedCount().intValue(), "市核任一人即可");
    }

    @Test
    void approve_cityOnlyMatchesItemCode_nameCollisionMeansNothing() {
        // 对照册里查不到这个事项码：就算名号撞得一个不差也不顶用。
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(1, 1));
        when(itemChangeBillSignMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(itemCatalogRowMapper.selectCount(any())).thenReturn(0);

        assertNull(service.approve(BILL_ID, "市员甲", "名号一模一样"));
        verify(itemChangeBillSignMapper, never()).insert(any());

        // 码对得上：市核过口，递到省定。
        when(itemCatalogRowMapper.selectCount(any())).thenReturn(1);
        when(itemChangeBillSignMapper.selectCount(any())).thenReturn(1);
        TItemChangeBill r = service.approve(BILL_ID, "市员甲", "码对得上");
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(1, r.getSignMode().intValue());
    }

    // ---------- 省定：两人并排，各记各的，代签不作数 ----------

    @Test
    void approve_provinceWithoutWatchRole_isRejected() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));
        assertNull(service.approve(BILL_ID, "省员甲", "没点盯项"));
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));
        assertNull(service.approve(BILL_ID, "省员甲", "乱点", 3));
        verify(itemChangeBillSignMapper, never()).insert(any());
    }

    @Test
    void approve_provinceOnlyOneName_billStuckNoSeal() {
        TItemChangeBill bill = runningBill(2, 1);
        bill.setSignCount(9); // 屏上摆的数不作数
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(bill);
        when(itemChangeBillSignMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(itemChangeBillSignMapper.selectCount(any())).thenReturn(1);

        TItemChangeBill r = service.approve(BILL_ID, "省员甲", "要素齐", 1);
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(0, r.getStatus().intValue(), "只到一名，见不着结案");
        assertEquals(1, r.getSignCount().intValue(), "签迹只点回一笔");
        assertEquals(0, r.getLocked().intValue());
        assertEquals(0, r.getPublicSync().intValue());
        assertNull(r.getSealTime());
    }

    @Test
    void approve_provinceTwoRolesParallel_sealsSyncsAndLocks() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));
        when(itemChangeBillSignMapper.selectList(any()))
                .thenReturn(Collections.singletonList(sign("省员甲", 1)));
        when(itemChangeBillSignMapper.selectCount(any())).thenReturn(2);

        TItemChangeBill r = service.approve(BILL_ID, "省员乙", "与上位名录合规矩", 2);
        assertEquals(1, r.getStatus().intValue());
        assertEquals(1, r.getLocked().intValue(), "落章后全单锁住");
        assertEquals(1, r.getPublicSync().intValue(), "对外名录跟着这一道同步");
        assertNotNull(r.getSealTime());
    }

    @Test
    void approve_samePersonTwiceOrSameRoleTwice_doesNotCount() {
        // 同一人想把两笔都落了：替人代落的那一笔不作数。
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));
        when(itemChangeBillSignMapper.selectList(any()))
                .thenReturn(Collections.singletonList(sign("省员甲", 1)));
        assertNull(service.approve(BILL_ID, "省员甲", "再代落合规口", 2));

        // 同角色已有亲笔者，后来者不能顶这一笔。
        assertNull(service.approve(BILL_ID, "省员丙", "再点要素口", 1));
        verify(itemChangeBillSignMapper, never()).insert(any());
    }

    // ---------- 止步：不予通过 / 撤回 ----------

    @Test
    void reject_stopsBillAtCurrentNodeForever() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(1, 1));
        when(itemChangeBillSignMapper.selectCount(any())).thenReturn(0);

        TItemChangeBill r = service.reject(BILL_ID, "市员甲", "不予通过");
        assertEquals(2, r.getStatus().intValue());
        assertEquals(1, r.getNodeNo().intValue(), "停在市核这一口");

        // 止步之后既不追加也不改写。
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(r);
        assertNull(service.approve(BILL_ID, "市员乙", "补签也没用"));
        assertNull(service.reject(BILL_ID, "市员乙", "再写一笔"));
    }

    @Test
    void withdraw_stopsBill_andSealedBillCannotMove() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(1, 1));
        TItemChangeBill r = service.withdraw(BILL_ID, "承办人");
        assertEquals(3, r.getStatus().intValue());

        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(r);
        assertNull(service.approve(BILL_ID, "市员甲", "撤回报送后不能签"));

        TItemChangeBill sealed = runningBill(2, 1);
        sealed.setStatus(1);
        sealed.setLocked(1);
        sealed.setPublicSync(1);
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(sealed);
        assertNull(service.approve(BILL_ID, "省员甲", "锁了", 1));
        assertNull(service.reject(BILL_ID, "省员甲", "锁了不能退"));
        assertNull(service.withdraw(BILL_ID, "承办人"));
    }

    // ---------- 省定表述打回：整批沉底，从县核重新攒 ----------

    @Test
    void rollback_fromProvince_sinksOldNamesAndRestartsAtCounty() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));

        TItemChangeBill r = service.rollback(BILL_ID, "表述不合规矩");
        assertEquals(0, r.getNodeNo().intValue(), "打回县核");
        assertEquals(2, r.getRoundNo().intValue(), "轮次加一");
        assertEquals(0, r.getSignCount().intValue());
        assertEquals(0, r.getPublicSync().intValue(), "对外旧记载不作数");
        // 先前各口名目整批沉底，且不与新批并排（del_flag 置 1）。
        verify(itemChangeBillSignMapper, times(1)).update(any(), any());
    }

    @Test
    void rollback_onlyAllowedAtProvinceBeforeSeal() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(1, 1));
        assertNull(service.rollback(BILL_ID, "市核无权打回"));

        TItemChangeBill sealed = runningBill(2, 2);
        sealed.setStatus(1);
        sealed.setLocked(1);
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(sealed);
        assertNull(service.rollback(BILL_ID, "落章后只能另开单"));
        verify(itemChangeBillSignMapper, never()).update(any(), any());
    }

    // ---------- 倒数复核：省定→市核→县核，一笔不差，两头合拢 ----------

    @Test
    void verifyTrace_sealedBill_countsFromProvinceBackToCounty() {
        TItemChangeBill sealed = runningBill(2, 1);
        sealed.setStatus(1);
        sealed.setLocked(1);
        sealed.setPublicSync(1);
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(sealed);
        when(itemChangeBillSignMapper.selectList(any()))
                .thenReturn(Arrays.asList(sign("省员甲", 1), sign("省员乙", 2)))
                .thenReturn(Collections.singletonList(sign("市员甲", null)))
                .thenReturn(Collections.singletonList(sign("县员甲", null)));

        ChangeBillTraceVo vo = service.verifyTrace(BILL_ID);
        assertTrue(vo.isPass());
        assertEquals(3, vo.getNodes().size());
        assertEquals("省定", vo.getNodes().get(0).getNodeName());
        assertEquals(Arrays.asList("省员甲", "省员乙"), vo.getNodes().get(0).getSigners());
        assertEquals("市核", vo.getNodes().get(1).getNodeName());
        assertEquals("县核", vo.getNodes().get(2).getNodeName());
        assertEquals(4, vo.getArchivedCount(), "档里存下4名");
        assertEquals(4, vo.getPublicCount(), "报给外头4名");
    }

    @Test
    void verifyTrace_oneNameMissingAtProvince_failsAndCountsDisagree() {
        TItemChangeBill sealed = runningBill(2, 1);
        sealed.setStatus(1);
        sealed.setLocked(1);
        sealed.setPublicSync(1);
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(sealed);
        when(itemChangeBillSignMapper.selectList(any()))
                .thenReturn(Collections.singletonList(sign("省员甲", 1)))
                .thenReturn(Collections.singletonList(sign("市员甲", null)))
                .thenReturn(Collections.singletonList(sign("县员甲", null)));

        ChangeBillTraceVo vo = service.verifyTrace(BILL_ID);
        assertFalse(vo.isPass());
        assertEquals(3, vo.getArchivedCount());
        assertEquals(4, vo.getPublicCount(), "两头名次合不拢，这一口便算没齐");
        assertTrue(vo.getProblems().stream().anyMatch(p -> p.contains("省定")));
    }

    @Test
    void verifyTrace_unsealedBill_fails() {
        when(itemChangeBillMapper.selectById(BILL_ID)).thenReturn(runningBill(2, 1));
        when(itemChangeBillSignMapper.selectList(any())).thenReturn(new ArrayList<>());
        ChangeBillTraceVo vo = service.verifyTrace(BILL_ID);
        assertFalse(vo.isPass());
        assertTrue(vo.getProblems().stream().anyMatch(p -> p.contains("落章")));
    }

    // ---------- helpers ----------

    private ChangeBillCreateReq req(String siteNo) {
        ChangeBillCreateReq req = new ChangeBillCreateReq();
        req.setItemCode(ITEM_CODE);
        req.setItemName("某行政确认事项");
        req.setBeforeFields("旧承办口径");
        req.setAfterFields("新承办口径");
        req.setChangeReason("去年八月补录，三本册三种话");
        req.setSiteNo(siteNo);
        return req;
    }

    private TItemDeptArch archIn编() {
        TItemDeptArch arch = new TItemDeptArch();
        arch.setSiteNo("ZW00");
        arch.setStatus(0);
        return arch;
    }

    private TItemChangeBill runningBill(int node, int round) {
        TItemChangeBill bill = new TItemChangeBill();
        bill.setId(BILL_ID);
        bill.setBillNo(ITEM_CODE + "-R" + round + "-1");
        bill.setItemCode(ITEM_CODE);
        bill.setItemName("某行政确认事项");
        bill.setBeforeFields("旧承办口径");
        bill.setAfterFields("新承办口径");
        bill.setChangeReason("补录对不上");
        bill.setSiteNo("ZW00");
        bill.setArchCheck(1);
        bill.setNodeNo(node);
        bill.setRoundNo(round);
        bill.setSeqNo(1);
        bill.setSignMode(node == 2 ? 1 : 0);
        bill.setNeedCount(node == 2 ? 2 : 1);
        bill.setSignCount(0);
        bill.setStatus(0);
        bill.setPublicSync(0);
        bill.setLocked(0);
        bill.setDelFlag(0);
        return bill;
    }

    private TItemChangeBillSign sign(String signer, Integer watchRole) {
        TItemChangeBillSign s = new TItemChangeBillSign();
        s.setBillId(BILL_ID);
        s.setSigner(signer);
        s.setWatchRole(watchRole);
        s.setVerdict(1);
        return s;
    }
}
