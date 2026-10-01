package com.fc.v2.model.custom;

/**
 * 核签单起件口报：县核收材料先点四样——
 * 事项码、改动前那几栏、改动后那几栏、改的缘故，四样缺一样，件起不来；
 * 另带承办底册代号过前置检。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class ChangeBillCreateReq {

    /** 事项码（市核对照只认码，不认名号） */
    private String itemCode;

    /** 事项名号（仅记载，名号撞得一个不差也不顶用） */
    private String itemName;

    /** 改动前那几栏 */
    private String beforeFields;

    /** 改动后那几栏 */
    private String afterFields;

    /** 改的缘故 */
    private String changeReason;

    /** 承办底册代号 */
    private String siteNo;

    /** 要素调整核签单号（不给则按事项码+轮次+件序配） */
    private String billNo;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getBeforeFields() {
        return beforeFields;
    }

    public void setBeforeFields(String beforeFields) {
        this.beforeFields = beforeFields;
    }

    public String getAfterFields() {
        return afterFields;
    }

    public void setAfterFields(String afterFields) {
        this.afterFields = afterFields;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }
}
