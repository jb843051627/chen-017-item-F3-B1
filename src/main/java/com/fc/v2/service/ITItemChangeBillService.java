package com.fc.v2.service;

import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.custom.ChangeBillCreateReq;
import com.fc.v2.model.custom.ChangeBillTraceVo;

/**
 * 事项要素调整逐级核签单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * 三道口各只有一处落点：县核收材料、市核做对照、省定落章并同步对外名录。
 * 页面上不另开一扇能签、能退、能改名的门，都归到这一对 approve/reject 来办。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemChangeBillService {

    /** 按主键回查单据 */
    TItemChangeBill selectTItemChangeBillById(Long id);

    /**
     * 起件：县核收材料。四样（事项码、改动前、改动后、缘故）缺一不可，
     * 承办底册须过前置检；缺一样或底册未过检，件停在县核起不来。
     * 同一项事项当年第二件，件序当场点名（seqNo 顺延）。
     */
    TItemChangeBill createBill(ChangeBillCreateReq req);

    /**
     * 签批一票（原方法，入参与返回不改）：落笔者本人在当前道次署名通过。
     * 省定两口并签时请用四参重载点明盯项角色。
     *
     * @return 更新后的单据；被拒（跳口/倒签/代签/封单/止步等）返回 null
     */
    TItemChangeBill approve(Long id, String approver, String comment);

    /**
     * 并排立的同名签法：省定一口两人并排落名，
     * watchRole=1 盯要素齐不齐，watchRole=2 盯与上位名录合不合规矩。
     * 两口各落各的，掐同一分钟也按两笔算；只到一名，件仍卡省定。
     *
     * @return 更新后的单据；被拒返回 null
     */
    TItemChangeBill approve(Long id, String approver, String comment, Integer watchRole);

    /**
     * 否决（原方法，入参与返回不改）：哪一口写下不予通过，这一件就此停在该口，
     * 往后既不追加也不改写。省定合规口若判“表述不合规矩”请用 rollback 整批沉底。
     *
     * @return 更新后的单据；被拒返回 null
     */
    TItemChangeBill reject(Long id, String approver, String comment);

    /**
     * 承办人把件撤回报送：这一件就此止步，不再往前挪。
     *
     * @return 更新后的单据；被拒返回 null
     */
    TItemChangeBill withdraw(Long id, String operator);

    /**
     * 省定以表述不合规矩打回：先前各口落过的名目整批沉底（del_flag=1，不与新批并排），
     * 这一件从县核那一口重新一层层往上攒，轮次加一。
     *
     * @return 更新后的单据；被拒返回 null
     */
    TItemChangeBill rollback(Long id, String comment);

    /**
     * 三道口感完后的倒数复核：改定版要素当日在名录上留下的记载，
     * 从省定倒数回县核，各级名目一笔不差；报外与存档由同一回点数交出，
     * 两头合不拢便算没齐。落了几个名只看这方法顺着签迹点出来给回什么。
     */
    ChangeBillTraceVo verifyTrace(Long id);
}
