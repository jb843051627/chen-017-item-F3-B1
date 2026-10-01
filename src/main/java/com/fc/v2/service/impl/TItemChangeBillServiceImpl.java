package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 规矩只有一份：一口该落几名、是任一人还是两名点齐，全由 {@link #needCountOf}、
 * {@link #signModeOf} 交出，起件、签批、打回、复核四处都照这一份，不许各写各的。
 * 落了几个名只顺着签迹表同一回点出来（{@link #activeSigns} 是唯一的点数口径），
 * 屏上回填的 sign_count 不作数；两头（签批关口、倒数复核）由同一道 {@link #nodeSatisfied}
 * 判定，一头松一头紧的事不再有。
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

    /** 道次总数，从头数到尾就这三关 */
    private static final int NODE_TOTAL = 3;

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

    // ----------------------------------------------------------------------
    // 规矩单一出处：全链各处只准照这里问，不准自己再写一份。
    // ----------------------------------------------------------------------

    /** 本口应落几名通过票：县核、市核任一名；省定两名点齐。 */
    private static int needCountOf(int node) {
        return node == NODE_PROVINCE ? 2 : 1;
    }

    /** 本口并签办法：县核、市核任一人；省定两名点齐。 */
    private static int signModeOf(int node) {
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
        // 起在哪一口，就照那一口的规矩立 mode/need——规矩只问唯一出处。
        bill.setSignMode(signModeOf(NODE_COUNTY));
        bill.setNeedCount(needCountOf(NODE_COUNTY));
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
        TItemChangeBill bill = loadMutable(id, approver);
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

        // 本口本轮还作数的签迹只点这一回，后面的计数、关口都用它，不再各点一遍。
        List<TItemChangeBillSign> signs = activeSigns(id, node, round);

        // 否决即刹链：本口已落下过不予通过的票，链子当场刹住，过后谁来都不能再签。
        // 已否决的件 loadMutable 也会挡，这里再挡一道，是给历史上“没刹住”的旧单兜底。
        if (containsVerdict(signs, VERDICT_REJECT)) {
            return null;
        }
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

        // 把这一轮三口的签迹一次摆齐（当前口用刚落的这笔接上已点到的签迹），计数、齐签判定都只认这一桌。
        List<List<TItemChangeBillSign>> roundSigns = loadRoundSigns(id, round, node, signs, sign);

        // 票随签走：本口本轮已落的通过票，顺着签迹点，屏上填的数不作数。
        int passedHere = passCount(roundSigns.get(node));
        // 票数跟着签批往上走，不在过口时清零——从头一关累计到当前关。
        bill.setSignCount(roundPassCount(roundSigns));

        if (node == NODE_PROVINCE) {
            bill.setSignMode(signModeOf(NODE_PROVINCE));
            bill.setNeedCount(needCountOf(NODE_PROVINCE));
            // 只到一名，件仍卡在省定，见不着结案。
            if (passedHere < needCountOf(NODE_PROVINCE)) {
                guardUpdate(bill, node, round);
                return bill;
            }
            // 两名点齐还不算完：齐签是整条链的事，县核、市核的通过票也得都在档里。
            // 历史上留下的要素调整核签单若缺了下头的票，这里不放行——新老单一道规矩。
            if (!chainSatisfied(roundSigns)) {
                guardUpdate(bill, node, round);
                return bill;
            }
            // 整条链三口齐签：省定落章，要素改动落定，对外名录同步，全单各栏一起锁住。
            bill.setNodeNo(NODE_PROVINCE);
            bill.setStatus(STATUS_PASS);
            bill.setPublicSync(1);
            bill.setLocked(1);
            bill.setSealTime(new Date());
            guardUpdate(bill, node, round);
            return bill;
        }

        // 县核、市核：一口应落一名，点齐才过口，按口次往上递，催得急也不顶用，不能跳着落名。
        if (passedHere < needCountOf(node)) {
            guardUpdate(bill, node, round);
            return bill;
        }
        int next = node + 1;
        bill.setNodeNo(next);
        bill.setSignMode(signModeOf(next));
        bill.setNeedCount(needCountOf(next));
        // sign_count 不清零：累计票数随件一起往上走。
        guardUpdate(bill, node, round);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill reject(Long id, String approver, String comment) {
        TItemChangeBill bill = loadMutable(id, approver);
        if (bill == null) {
            return null;
        }
        int node = bill.getNodeNo() == null ? NODE_COUNTY : bill.getNodeNo();
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();

        List<TItemChangeBillSign> signs = activeSigns(id, node, round);
        // 已落过否决票的口，链子早刹住了，不另写一张“不予通过”。
        if (containsVerdict(signs, VERDICT_REJECT)) {
            return null;
        }

        // 不予通过也得是本口亲笔者落下的结论。
        TItemChangeBillSign sign = new TItemChangeBillSign();
        sign.setBillId(id);
        sign.setNodeNo(node);
        sign.setRoundNo(round);
        sign.setSeq(signs.size() + 1);
        sign.setSigner(approver);
        sign.setWatchRole(node == NODE_PROVINCE ? ROLE_COMPLIANCE : null);
        sign.setVerdict(VERDICT_REJECT);
        sign.setCommentText(comment);
        sign.setSignTime(new Date());
        sign.setDelFlag(0);
        this.itemChangeBillSignMapper.insert(sign);

        // 哪一口写下不予通过，这一件就此停在该口，往后既不追加也不改写。
        bill.setStatus(STATUS_VETO);
        // 否决票不计入通过票数。
        guardUpdate(bill, node, round);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill withdraw(Long id, String operator) {
        TItemChangeBill bill = this.itemChangeBillMapper.selectById(id);
        if (bill == null || StringUtils.isEmpty(operator)) {
            return null;
        }
        // 已止步、已落章锁住的件，撤不动也改不动。
        if (bill.getStatus() == null || bill.getStatus() != STATUS_RUNNING) {
            return null;
        }
        if (Integer.valueOf(1).equals(bill.getLocked())) {
            return null;
        }
        int node = bill.getNodeNo() == null ? NODE_COUNTY : bill.getNodeNo();
        int round = bill.getRoundNo() == null ? 1 : bill.getRoundNo();
        // 承办人把件撤回报送，这一件就此止步。
        bill.setStatus(STATUS_WITHDRAWN);
        // 同样带旧口旧轮把关：半途若已被签批挪了口，这条撤回落 0 行，整笔回滚。
        guardUpdate(bill, node, round);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill rollback(Long id, String comment) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        // 只有省定那一口能以“表述不合规矩”打回；已落章的件另开一张单说话。
        if (r == null || !Integer.valueOf(NODE_PROVINCE).equals(r.getNodeNo())
                || Integer.valueOf(1).equals(r.getLocked())
                || (r.getStatus() != null && r.getStatus() != STATUS_RUNNING)) {
            return null;
        }
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();
        int nextRound = round + 1;

        // 同一道规矩：这一轮任一口已落下过不予通过的票，链子既已刹住，就不许借“表述打回”沉底复活。
        // 兼容历史旧单——哪怕 status 当年没置上，签迹里的否决票也算数。
        for (int node = NODE_COUNTY; node <= NODE_PROVINCE; node++) {
            if (containsVerdict(activeSigns(id, node, round), VERDICT_REJECT)) {
                return null;
            }
        }

        // 先前各口落过的名目整批沉底：沉下去的那批与新补的那批不并排摆。
        this.itemChangeBillSignMapper.update(null, new UpdateWrapper<TItemChangeBillSign>()
                .eq("bill_id", id)
                .eq("round_no", round)
                .eq("del_flag", 0)
                .set("del_flag", 1)
                .set("comment_text", comment));

        // 从县核那一口重新一层层往上攒。
        r.setRoundNo(nextRound);
        r.setNodeNo(NODE_COUNTY);
        r.setSignMode(signModeOf(NODE_COUNTY));
        r.setNeedCount(needCountOf(NODE_COUNTY));
        // 旧轮次的票整批沉了底，新一轮的票数从 0 重新攒——二次提交不会顶着旧票报满员。
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        // 对外那一页跟着章走过一回，打回后旧记载不作数，待新一轮落章再同步。
        r.setPublicSync(0);
        // 条件带旧口旧轮：半途若另有一笔签批/打回改了单，这条更新落 0 行，整笔回滚。
        guardUpdate(r, NODE_PROVINCE, round);
        return r;
    }

    @Override
    public ChangeBillTraceVo verifyTrace(Long id) {
        ChangeBillTraceVo vo = new ChangeBillTraceVo();
        vo.setBillId(id);
        TItemChangeBill bill = this.itemChangeBillMapper.selectById(id);
        if (bill == null) {
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

        // 从省定倒数回县核，每一口都用与签批同一道判定复核。
        int archivedTotal = 0;
        int publicTotal = 0;
        for (int node = NODE_PROVINCE; node >= NODE_COUNTY; node--) {
            List<TItemChangeBillSign> signs = activeSigns(id, node, round);
            List<String> names = new ArrayList<>();
            for (TItemChangeBillSign s : signs) {
                names.add(s.getSigner());
            }
            int need = needCountOf(node);
            int signed = passCount(signs);
            archivedTotal += signed;
            publicTotal += need;

            // 齐不齐由 nodeSatisfied 一锤定音——签批放行看它，复核也看它，两头同源。
            if (!nodeSatisfied(node, signs)) {
                ok = false;
                if (containsVerdict(signs, VERDICT_REJECT)) {
                    vo.getProblems().add(NODE_NAMES[node] + "夹着不予通过的票，链子本应刹住");
                } else if (signed != need) {
                    vo.getProblems().add(NODE_NAMES[node] + "应落" + need + "名，签迹只有" + signed + "名");
                }
                if (hasDuplicateSigner(signs)) {
                    vo.getProblems().add(NODE_NAMES[node] + "同一落名人占了两笔，代签不作数");
                }
                if (node == NODE_PROVINCE && !provinceRolesComplete(signs)) {
                    vo.getProblems().add(NODE_NAMES[node] + "盯要素、盯合规两笔未点齐");
                }
            }
            vo.getNodes().add(new ChangeBillTraceVo.NodeTrace(node, NODE_NAMES[node], need, signed, names));
        }

        // 同一回点数：报给外头看的几名与档里存下的几名由这一回交出，两头合不拢便算没齐。
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
     * 取出还能落笔的单据：不存在、已止步、已封单，一律不给签、不给退。
     * approve 与 reject 共用这道关口，两头规矩同一个，不会一头放、一头拦。
     */
    private TItemChangeBill loadMutable(Long id, String approver) {
        if (id == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        TItemChangeBill bill = this.itemChangeBillMapper.selectById(id);
        if (bill == null) {
            return null;
        }
        // 不予通过、撤回的件就此停住；省定落章后全单锁住，再动须另开一张单。
        if (bill.getStatus() == null || bill.getStatus() != STATUS_RUNNING) {
            return null;
        }
        if (Integer.valueOf(1).equals(bill.getLocked())) {
            return null;
        }
        return bill;
    }

    /**
     * 本口本轮还作数的签迹，按落名先后排下。全链“落了几笔”只认这一个口径，
     * 绝不在另一处再 selectCount 点一遍——并发叠上回滚时两处才不会点出两样。
     */
    private List<TItemChangeBillSign> activeSigns(Long billId, int node, int round) {
        return this.itemChangeBillSignMapper.selectList(new QueryWrapper<TItemChangeBillSign>()
                .eq("bill_id", billId)
                .eq("node_no", node)
                .eq("round_no", round)
                .eq("del_flag", 0)
                .orderByAsc("seq"));
    }

    /**
     * 把这一轮三口的作数签迹一次取齐。当前口直接用落笔前点到的那一摞接上刚落的这笔，
     * 不再回库重点——一笔签批内对签迹只有这一回读，并发叠回滚时点不出两样。
     */
    private List<List<TItemChangeBillSign>> loadRoundSigns(Long billId, int round, int currentNode,
            List<TItemChangeBillSign> currentSigns, TItemChangeBillSign justSigned) {
        List<List<TItemChangeBillSign>> all = new ArrayList<>(NODE_TOTAL);
        for (int node = NODE_COUNTY; node <= NODE_PROVINCE; node++) {
            if (node == currentNode) {
                List<TItemChangeBillSign> here = new ArrayList<>(currentSigns);
                here.add(justSigned);
                all.add(here);
            } else {
                all.add(activeSigns(billId, node, round));
            }
        }
        return all;
    }

    private static boolean containsVerdict(List<TItemChangeBillSign> signs, int verdict) {
        for (TItemChangeBillSign s : signs) {
            if (Integer.valueOf(verdict).equals(s.getVerdict())) {
                return true;
            }
        }
        return false;
    }

    /** 一摞签迹里通过票的笔数（不予通过、沉底的都不算）。 */
    private static int passCount(List<TItemChangeBillSign> signs) {
        int n = 0;
        for (TItemChangeBillSign s : signs) {
            if (Integer.valueOf(VERDICT_PASS).equals(s.getVerdict())) {
                n++;
            }
        }
        return n;
    }

    private static int roundPassCount(List<List<TItemChangeBillSign>> roundSigns) {
        int total = 0;
        for (List<TItemChangeBillSign> signs : roundSigns) {
            total += passCount(signs);
        }
        return total;
    }

    /** 省定两口角色是否点齐：一盯要素、一盯合规，各一笔。 */
    private static boolean provinceRolesComplete(List<TItemChangeBillSign> signs) {
        boolean hasElement = false;
        boolean hasCompliance = false;
        for (TItemChangeBillSign s : signs) {
            if (Integer.valueOf(ROLE_ELEMENT).equals(s.getWatchRole())) {
                hasElement = true;
            }
            if (Integer.valueOf(ROLE_COMPLIANCE).equals(s.getWatchRole())) {
                hasCompliance = true;
            }
        }
        return hasElement && hasCompliance;
    }

    /** 同一口里同一落名人是否占了两笔（代签、重复点）。 */
    private static boolean hasDuplicateSigner(List<TItemChangeBillSign> signs) {
        List<String> seen = new ArrayList<>();
        for (TItemChangeBillSign s : signs) {
            if (s.getSigner() != null && seen.contains(s.getSigner())) {
                return true;
            }
            seen.add(s.getSigner());
        }
        return false;
    }

    /**
     * 一道关口的唯一判定：本口这摞签迹算不算“齐签通过”。
     * 签批放行、倒数复核两头都只问它——应落几名照 {@link #needCountOf}，
     * 不许夹否决票、不许同人占两笔，省定还须两角色点齐。
     */
    private static boolean nodeSatisfied(int node, List<TItemChangeBillSign> signs) {
        if (containsVerdict(signs, VERDICT_REJECT)) {
            return false;
        }
        if (passCount(signs) != needCountOf(node)) {
            return false;
        }
        if (hasDuplicateSigner(signs)) {
            return false;
        }
        if (node == NODE_PROVINCE && !provinceRolesComplete(signs)) {
            return false;
        }
        return true;
    }

    /** 整条链三口是否都齐签——省定落章前的最后一道，缺哪一关的票都不落章。 */
    private static boolean chainSatisfied(List<List<TItemChangeBillSign>> roundSigns) {
        for (int node = NODE_COUNTY; node <= NODE_PROVINCE; node++) {
            if (!nodeSatisfied(node, roundSigns.get(node))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 带关口条件落库：只在单据仍停在“旧口、旧轮、在核、未锁”时更新才作数。
     * 并发叠上回滚/另一笔签批时，必有一处更新落 0 行——随即抛错整笔回滚，
     * 两处不会各执一套结果。
     */
    private void guardUpdate(TItemChangeBill bill, Integer expectNode, Integer expectRound) {
        UpdateWrapper<TItemChangeBill> uw = new UpdateWrapper<TItemChangeBill>()
                .eq("id", bill.getId())
                .eq("status", STATUS_RUNNING)
                .eq("locked", 0);
        if (expectNode != null) {
            uw.eq("node_no", expectNode.intValue());
        }
        if (expectRound != null) {
            uw.eq("round_no", expectRound.intValue());
        }
        int rows = this.itemChangeBillMapper.update(bill, uw);
        if (rows != 1) {
            throw new IllegalStateException("单据已被另一笔签批或打回改动，请重新过目后再办");
        }
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
