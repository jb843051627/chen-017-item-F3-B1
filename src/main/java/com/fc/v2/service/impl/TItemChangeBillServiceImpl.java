package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemChangeBillMapper;
import com.fc.v2.mapper.auto.TItemChangeBillSignMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.auto.TItemChangeBillSign;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.model.custom.ChangeBillCreateReq;
import com.fc.v2.model.custom.ChangeBillTraceVo;
import com.fc.v2.service.ITItemChangeBillService;
import com.fc.v2.util.StringUtils;

/**
 * 事项要素调整逐级核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 三道口各只有一处落点，都归到 approve/reject 这一对方法来办：
 * 0 县核——收材料（四样点齐 + 承办底册过前置检）；
 * 1 市核——拿县里递上来的同省里那册对照册比着看，只认事项码；
 * 2 省定——两人并排落名（一盯要素、一盯合规），落章后要素落定、对外名录同步、全单锁住。
 *
 * 全链只有两道共用规矩，谁要用谁来取，不许各写各的：
 * 一、哪一口该落几名、并签办法如何，只看 needCountForNode/signModeForNode 这一份；
 * 二、还能不能动这张单，只过 loadMutable 这一道闸（已删、止步、封单一律不给动），
 *    落库那一瞬再过 mutableWrapper 这道闸——读罢被人抢先的，整笔连同签迹一起回滚。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemChangeBillServiceImpl implements ITItemChangeBillService {

    /** 道次：县核收材料 */
    private static final int NODE_COUNTY = 0;
    /** 道次：市核做对照 */
    private static final int NODE_CITY = 1;
    /** 道次：省定落章 */
    private static final int NODE_PROVINCE = 2;

    private static final String[] NODE_NAMES = {"县核", "市核", "省定"};

    /** 同口并签办法：任一人 */
    private static final int MODE_OR = 0;
    /** 同口并签办法：两名点齐 */
    private static final int MODE_AND = 1;

    /** 报批情形 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;
    private static final int STATUS_WITHDRAWN = 3;

    /** 省定盯项角色 */
    private static final int ROLE_ELEMENT = 1;
    private static final int ROLE_COMPLIANCE = 2;

    /** 签迹结论 */
    private static final int VERDICT_REJECT = 0;
    private static final int VERDICT_PASS = 1;

    @javax.annotation.Resource
    private TItemChangeBillMapper itemChangeBillMapper;

    @javax.annotation.Resource
    private TItemChangeBillSignMapper itemChangeBillSignMapper;

    @javax.annotation.Resource
    private TItemDeptArchMapper itemDeptArchMapper;

    @javax.annotation.Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    /**
     * 哪一口该落几名——全链只此一份：县核、市核任一人，省定两名点齐。
     * 起件、过口、打回、复核都照这一份点名，谁也不许另立尺子。
     */
    private static int needCountForNode(int node) {
        return node == NODE_PROVINCE ? 2 : 1;
    }

    /** 哪一口并签办法如何——与应落名数同一把尺。 */
    private static int signModeForNode(int node) {
        return node == NODE_PROVINCE ? MODE_AND : MODE_OR;
    }

    @Override
    public TItemChangeBill selectTItemChangeBillById(Long id) {
        return this.itemChangeBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill createBill(ChangeBillCreateReq req) {
        if (req == null) {
            return null;
        }
        // 县核先点四样：事项码、改动前、改动后、缘故——缺一样，件起不来。
        if (StringUtils.isEmpty(req.getItemCode())
                || StringUtils.isEmpty(req.getBeforeFields())
                || StringUtils.isEmpty(req.getAfterFields())
                || StringUtils.isEmpty(req.getChangeReason())) {
            return null;
        }

        TItemChangeBill bill = new TItemChangeBill();
        bill.setItemCode(req.getItemCode().trim());
        bill.setItemName(req.getItemName());
        bill.setBeforeFields(req.getBeforeFields());
        bill.setAfterFields(req.getAfterFields());
        bill.setChangeReason(req.getChangeReason());
        bill.setSiteNo(StringUtils.isNotEmpty(req.getSiteNo()) ? req.getSiteNo().trim() : null);

        // 承办底册前置检：底册不存在或已撤并，检便不过，件停在县核。
        TItemDeptArch arch = null;
        if (StringUtils.isNotEmpty(bill.getSiteNo())) {
            arch = this.itemDeptArchMapper.selectOne(new QueryWrapper<TItemDeptArch>()
                    .eq("site_no", bill.getSiteNo())
                    .eq("del_flag", 0));
        }
        boolean archPass = arch != null && (arch.getStatus() == null || arch.getStatus() == 0);
        bill.setArchCheck(archPass ? 1 : 0);

        // 同一项事项当年件序当场点名（第二轮重新从 1 攒）。
        int round = 1;
        Integer seq = this.itemChangeBillMapper.selectCount(new QueryWrapper<TItemChangeBill>()
                .eq("item_code", bill.getItemCode())
                .eq("round_no", round)
                .eq("del_flag", 0));
        int seqNo = (seq == null ? 0 : seq) + 1;
        bill.setSeqNo(seqNo);
        bill.setRoundNo(round);
        bill.setBillNo(StringUtils.isNotEmpty(req.getBillNo())
                ? req.getBillNo().trim()
                : bill.getItemCode() + "-R" + round + "-" + seqNo);

        bill.setNodeNo(NODE_COUNTY);
        // 县核任一人即可放行；应落几名、并签办法都照共用那把尺。
        bill.setSignMode(signModeForNode(NODE_COUNTY));
        bill.setNeedCount(needCountForNode(NODE_COUNTY));
        bill.setSignCount(0);
        // 前置检没过，件起得来但停在县核，往后再齐也不往前挪。
        bill.setStatus(STATUS_RUNNING);
        bill.setPublicSync(0);
        bill.setLocked(0);
        bill.setDelFlag(0);
        this.itemChangeBillMapper.insert(bill);
        return bill;
    }

    @Override
    public TItemChangeBill approve(Long id, String approver, String comment) {
        return approve(id, approver, comment, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill approve(Long id, String approver, String comment, Integer watchRole) {
        if (StringUtils.isEmpty(approver)) {
            return null;
        }
        TItemChangeBill bill = loadMutable(id);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo() == null ? NODE_COUNTY : bill.getNodeNo();
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();

        // 承办底册没过前置那道检的，件停在县核，往后再齐也不往前挪。
        if (node == NODE_COUNTY && !Integer.valueOf(1).equals(bill.getArchCheck())) {
            return null;
        }

        // 省定两口并排：角色必须点明，一人盯要素、一人盯合规，两笔各记各的。
        Integer role = null;
        if (node == NODE_PROVINCE) {
            if (watchRole == null || (watchRole != ROLE_ELEMENT && watchRole != ROLE_COMPLIANCE)) {
                return null;
            }
            role = watchRole;
        }

        List<TItemChangeBillSign> signs = activeSigns(id, node, round);

        // 同一角色一人只落自己一笔；同人再点、掐同一分钟再点都不另算一笔。
        // 替人代落：落名人与先前亲笔者同一人，冒充另一角色的那一笔不作数。
        for (TItemChangeBillSign s : signs) {
            if (approver.equals(s.getSigner())) {
                return null;
            }
            if (role != null && role.equals(s.getWatchRole())) {
                return null;
            }
        }

        // 市核对照：同省里那册对照册比着看，认的是事项码不是名号。
        if (node == NODE_CITY && !catalogMatches(bill.getItemCode())) {
            return null;
        }

        TItemChangeBillSign sign = new TItemChangeBillSign();
        sign.setBillId(id);
        sign.setNodeNo(node);
        sign.setRoundNo(round);
        sign.setSeq(signs.size() + 1);
        sign.setSigner(approver);
        sign.setWatchRole(role);
        sign.setVerdict(VERDICT_PASS);
        sign.setCommentText(comment);
        sign.setSignTime(new Date());
        sign.setDelFlag(0);
        this.itemChangeBillSignMapper.insert(sign);

        // 落了几个名，不由页面给框，只顺着签迹同一回点数出来；
        // 过口齐不齐只数"通过"的签迹——否决票不凑数，历史件沉着否决票的口也一样。
        int signed = countActiveSigns(id, node, round, VERDICT_PASS);
        bill.setSignCount(signed);

        if (node == NODE_PROVINCE) {
            bill.setSignMode(signModeForNode(node));
            bill.setNeedCount(needCountForNode(node));
            // 只到一名，件仍卡在省定，见不着结案。
            if (signed < needCountForNode(node)) {
                return saveBillOrRollback(bill, node, round);
            }
            // 两名点齐：省定落章，要素改动落定，对外名录同步，全单各栏一起锁住。
            bill.setStatus(STATUS_PASS);
            bill.setPublicSync(1);
            bill.setLocked(1);
            bill.setSealTime(new Date());
            return saveBillOrRollback(bill, node, round);
        }

        // 县核、市核：一笔即过口，按口次往上递，催得急也不顶用，不能跳着落名。
        int next = node + 1;
        bill.setNodeNo(next);
        bill.setSignMode(signModeForNode(next));
        bill.setNeedCount(needCountForNode(next));
        bill.setSignCount(0);
        return saveBillOrRollback(bill, node, round);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill reject(Long id, String approver, String comment) {
        if (StringUtils.isEmpty(approver)) {
            return null;
        }
        TItemChangeBill bill = loadMutable(id);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo() == null ? NODE_COUNTY : bill.getNodeNo();
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();

        // 与签批同一个规矩，关口两头一起看：同一口一人只落一笔——
        // 亲笔者落过"通过"的口，同一人不能再补一笔"不予通过"。
        for (TItemChangeBillSign s : activeSigns(id, node, round)) {
            if (approver.equals(s.getSigner())) {
                return null;
            }
        }

        // 不予通过也得是本口亲笔者落下的结论；否决不冒充盯项角色，角色栏留空。
        TItemChangeBillSign sign = new TItemChangeBillSign();
        sign.setBillId(id);
        sign.setNodeNo(node);
        sign.setRoundNo(round);
        sign.setSeq(countActiveSigns(id, node, round) + 1);
        sign.setSigner(approver);
        sign.setWatchRole(null);
        sign.setVerdict(VERDICT_REJECT);
        sign.setCommentText(comment);
        sign.setSignTime(new Date());
        sign.setDelFlag(0);
        this.itemChangeBillSignMapper.insert(sign);

        // 否决票也是落名：本口已落名数跟着签迹同一回点数出来，不许停在原地。
        bill.setSignCount(countActiveSigns(id, node, round));
        // 哪一口写下不予通过，这一件就此停在该口，往后既不追加也不改写。
        bill.setStatus(STATUS_VETO);
        return saveBillOrRollback(bill, node, round);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill withdraw(Long id, String operator) {
        if (StringUtils.isEmpty(operator)) {
            return null;
        }
        TItemChangeBill bill = loadMutable(id);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo() == null ? NODE_COUNTY : bill.getNodeNo();
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();
        // 承办人把件撤回报送，这一件就此止步。
        bill.setStatus(STATUS_WITHDRAWN);
        return saveBillOrRollback(bill, node, round);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill rollback(Long id, String comment) {
        TItemChangeBill r = loadMutable(id);
        // 只有省定那一口能以“表述不合规矩”打回；已落章的件另开一张单说话。
        if (r == null || !Integer.valueOf(NODE_PROVINCE).equals(r.getNodeNo())) {
            return null;
        }
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();

        // 先前各口落过的名目整批沉底：本轮连同往前各轮还浮着的旧批一起沉，
        // 历史件前几轮没沉干净的这一回一并收下去，二次提交不再被旧签迹顶住。
        // 沉底只摘牌不涂字——旧签注是审计要对照的东西，一个字也不改。
        this.itemChangeBillSignMapper.update(null, new UpdateWrapper<TItemChangeBillSign>()
                .eq("bill_id", id)
                .le("round_no", round)
                .eq("del_flag", 0)
                .set("del_flag", 1));

        // 从县核那一口重新一层层往上攒。
        int nextRound = round + 1;
        r.setRoundNo(nextRound);
        r.setNodeNo(NODE_COUNTY);
        r.setSignMode(signModeForNode(NODE_COUNTY));
        r.setNeedCount(needCountForNode(NODE_COUNTY));
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        // 对外那一页跟着章走过一回，打回后旧记载不作数，待新一轮落章再同步。
        r.setPublicSync(0);
        // 打回的说法记在单上，不动任何一笔旧签注。
        if (StringUtils.isNotEmpty(comment)) {
            r.setRemark(comment);
        }
        return saveBillOrRollback(r, NODE_PROVINCE, round);
    }

    @Override
    public ChangeBillTraceVo verifyTrace(Long id) {
        ChangeBillTraceVo vo = new ChangeBillTraceVo();
        vo.setBillId(id);
        TItemChangeBill bill = this.itemChangeBillMapper.selectById(id);
        if (bill == null || Integer.valueOf(1).equals(bill.getDelFlag())) {
            vo.setPass(false);
            vo.getProblems().add("单据不存在");
            return vo;
        }
        vo.setBillNo(bill.getBillNo());
        vo.setItemCode(bill.getItemCode());
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();
        vo.setRoundNo(round);

        boolean ok = true;

        // 省定章落下、对外名录已同步，才谈得上“当日那一屏对得上号”。
        if (!Integer.valueOf(STATUS_PASS).equals(bill.getStatus())
                || !Integer.valueOf(1).equals(bill.getLocked())
                || !Integer.valueOf(1).equals(bill.getPublicSync())) {
            ok = false;
            vo.getProblems().add("省定尚未落章或对外名录未同步");
        }

        // 从省定倒数回县核，每一口都经得起复核。
        int archivedTotal = 0;
        for (int node = NODE_PROVINCE; node >= NODE_COUNTY; node--) {
            List<TItemChangeBillSign> signs = activeSigns(id, node, round);
            // 点落名只点"通过"的签迹；还浮着的不予通过签迹是另一笔账，单列出来。
            List<TItemChangeBillSign> passSigns = new ArrayList<>();
            for (TItemChangeBillSign s : signs) {
                if (Integer.valueOf(VERDICT_PASS).equals(s.getVerdict())) {
                    passSigns.add(s);
                }
            }
            List<String> names = new ArrayList<>();
            for (TItemChangeBillSign s : passSigns) {
                names.add(s.getSigner());
            }
            // 应落几名与过口、打回、起件同一把尺，不另立。
            int need = needCountForNode(node);
            int signed = passSigns.size();
            archivedTotal += signed;

            if (signed != need) {
                ok = false;
                vo.getProblems().add(NODE_NAMES[node] + "应落" + need + "名，签迹只有" + signed + "名");
            }
            if (signs.size() != signed) {
                ok = false;
                vo.getProblems().add(NODE_NAMES[node] + "有" + (signs.size() - signed) + "笔不予通过签迹未沉底");
            }
            // 一笔不差：同一口里不许同人落两笔，省定两口角色须齐。
            if (node == NODE_PROVINCE) {
                boolean hasElement = false;
                boolean hasCompliance = false;
                for (TItemChangeBillSign s : passSigns) {
                    if (Integer.valueOf(ROLE_ELEMENT).equals(s.getWatchRole())) {
                        hasElement = true;
                    }
                    if (Integer.valueOf(ROLE_COMPLIANCE).equals(s.getWatchRole())) {
                        hasCompliance = true;
                    }
                }
                if (!hasElement || !hasCompliance) {
                    ok = false;
                    vo.getProblems().add(NODE_NAMES[node] + "盯要素、盯合规两笔未点齐");
                }
            }
            vo.getNodes().add(new ChangeBillTraceVo.NodeTrace(node, NODE_NAMES[node], need, signed, names));
        }

        // 同一回点数：报给外头看的几名 = 各口应落之和，与点名的尺子同一把，两头才合得拢。
        int publicTotal = 0;
        for (int node = NODE_COUNTY; node <= NODE_PROVINCE; node++) {
            publicTotal += needCountForNode(node);
        }
        vo.setArchivedCount(archivedTotal);
        vo.setPublicCount(publicTotal);
        if (archivedTotal != publicTotal) {
            ok = false;
            vo.getProblems().add("报外" + publicTotal + "名与存档" + archivedTotal + "名合不拢");
        }

        vo.setPass(ok);
        return vo;
    }

    /**
     * 还能不能动这张单，全链只此一道闸：不存在、已删除、已止步、已封单，
     * 一律不给签、不给退、不给撤、不给打回。历史件情形栏是空的，按在核办。
     */
    private TItemChangeBill loadMutable(Long id) {
        if (id == null) {
            return null;
        }
        TItemChangeBill bill = this.itemChangeBillMapper.selectById(id);
        if (bill == null) {
            return null;
        }
        // 已删的件：档上不存在，谁来了也不给动。
        if (Integer.valueOf(1).equals(bill.getDelFlag())) {
            return null;
        }
        // 不予通过、撤回的件就此停住；省定落章后全单锁住，再动须另开一张单。
        if (bill.getStatus() != null && bill.getStatus() != STATUS_RUNNING) {
            return null;
        }
        if (Integer.valueOf(1).equals(bill.getLocked())) {
            return null;
        }
        return bill;
    }

    /**
     * 落库这一瞬再过一道闸：只准动"读到的那个状态"（同口同轮、在核、未封、未删）。
     * 读罢被人抢先否决、打回、落章、删件的，这一下扑空——
     * 整笔连同刚落的签迹一起回滚，两边只认一个结果，谁也不踩谁。
     */
    private TItemChangeBill saveBillOrRollback(TItemChangeBill bill, int node, int round) {
        int rows = this.itemChangeBillMapper.update(bill, mutableWrapper(bill.getId(), node, round));
        if (rows != 1) {
            markRollbackOnly();
            return null;
        }
        return bill;
    }

    private UpdateWrapper<TItemChangeBill> mutableWrapper(Long id, int node, int round) {
        return new UpdateWrapper<TItemChangeBill>()
                .eq("id", id)
                .eq("del_flag", 0)
                .eq("node_no", node)
                .eq("round_no", round)
                .and(w -> w.eq("status", STATUS_RUNNING).or().isNull("status"))
                .and(w -> w.eq("locked", 0).or().isNull("locked"));
    }

    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // 没有事务上下文（如单测直调）：签迹本就没真落，返回 null 表态即可。
        }
    }

    /**
     * 本口本轮还作数的签迹，按落名先后排下。
     */
    private List<TItemChangeBillSign> activeSigns(Long billId, int node, int round) {
        return this.itemChangeBillSignMapper.selectList(new QueryWrapper<TItemChangeBillSign>()
                .eq("bill_id", billId)
                .eq("node_no", node)
                .eq("round_no", round)
                .eq("del_flag", 0)
                .orderByAsc("seq"));
    }

    private int countActiveSigns(Long billId, int node, int round) {
        return countActiveSigns(billId, node, round, null);
    }

    /**
     * 本口本轮还作数的签迹点数；verdict 不空时只点那一种结论的——
     * 过口齐不齐只数"通过"，否决票不凑数。
     */
    private int countActiveSigns(Long billId, int node, int round, Integer verdict) {
        QueryWrapper<TItemChangeBillSign> q = new QueryWrapper<TItemChangeBillSign>()
                .eq("bill_id", billId)
                .eq("node_no", node)
                .eq("round_no", round)
                .eq("del_flag", 0);
        if (verdict != null) {
            q.eq("verdict", verdict);
        }
        Integer c = this.itemChangeBillSignMapper.selectCount(q);
        return c == null ? 0 : c;
    }

    /**
     * 市核对照：拿县里递上来的同省里那册对照册比着看，
     * 认的是事项码不是名号——名号撞得一个不差，码对不上也不顶用。
     */
    private boolean catalogMatches(String itemCode) {
        if (StringUtils.isEmpty(itemCode)) {
            return false;
        }
        Integer hits = this.itemCatalogRowMapper.selectCount(new QueryWrapper<TItemCatalogRow>()
                .eq("item_code", itemCode)
                .eq("del_flag", 0));
        return hits != null && hits > 0;
    }
}
