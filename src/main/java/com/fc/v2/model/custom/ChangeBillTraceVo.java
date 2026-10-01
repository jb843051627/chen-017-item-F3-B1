package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

/**
 * 三道口感完后的倒数复核：改定版要素当日在名录上留下的记载，
 * 从省定倒数回县核，各级落过的名目一笔不差，且每一口经得起复核。
 * 报给外头看的几名与档里存下的几名，由这同一回点数交出来。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class ChangeBillTraceVo {

    /** 核签单主键 */
    private Long billId;

    /** 核签单号 */
    private String billNo;

    /** 事项码 */
    private String itemCode;

    /** 重走轮次（复核的是落定那一轮） */
    private Integer roundNo;

    /** 是否每一口都经得起复核（口次连贯、名目一笔不差、对外已同步） */
    private boolean pass;

    /** 对不上号处的人话（pass=true 时为空） */
    private List<String> problems = new ArrayList<>();

    /** 从省定倒数回县核，逐口交出来的名目（顺序：省定→市核→县核） */
    private List<NodeTrace> nodes = new ArrayList<>();

    /** 同一回点数：档里存下的总数 */
    private int archivedCount;

    /** 同一回点数：报给外头看的总数（= 逐口名目之和，两头须合拢） */
    private int publicCount;

    public static class NodeTrace {
        /** 口次 0县核 1市核 2省定 */
        private Integer nodeNo;
        /** 口名 */
        private String nodeName;
        /** 本口应落名数 */
        private Integer needCount;
        /** 本口签迹点数出的已落名数 */
        private Integer signCount;
        /** 按落名先后排下的名目 */
        private List<String> signers = new ArrayList<>();

        public NodeTrace() {
        }

        public NodeTrace(Integer nodeNo, String nodeName, Integer needCount, Integer signCount, List<String> signers) {
            this.nodeNo = nodeNo;
            this.nodeName = nodeName;
            this.needCount = needCount;
            this.signCount = signCount;
            this.signers = signers;
        }

        public Integer getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(Integer nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public Integer getNeedCount() {
            return needCount;
        }

        public void setNeedCount(Integer needCount) {
            this.needCount = needCount;
        }

        public Integer getSignCount() {
            return signCount;
        }

        public void setSignCount(Integer signCount) {
            this.signCount = signCount;
        }

        public List<String> getSigners() {
            return signers;
        }

        public void setSigners(List<String> signers) {
            this.signers = signers;
        }
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }

    public List<NodeTrace> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeTrace> nodes) {
        this.nodes = nodes;
    }

    public int getArchivedCount() {
        return archivedCount;
    }

    public void setArchivedCount(int archivedCount) {
        this.archivedCount = archivedCount;
    }

    public int getPublicCount() {
        return publicCount;
    }

    public void setPublicCount(int publicCount) {
        this.publicCount = publicCount;
    }
}
