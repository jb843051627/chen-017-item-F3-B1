package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 事项要素调整逐级核签单对象 t_item_change_bill
 * 三道口：0县核收材料 / 1市核做对照 / 2省定落章并同步对外名录。
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_item_change_bill")
@ApiModel(value = "TItemChangeBill", description = "事项要素调整逐级核签单")
public class TItemChangeBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 要素调整核签单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "要素调整核签单号")
    private String billNo;

    /** 事项码（三道口算件、市核对照只认它，不认名号） */
    @TableField("item_code")
    @ApiModelProperty(value = "事项码")
    private String itemCode;

    /** 事项名号（仅记载用，不作对照凭据） */
    @TableField("item_name")
    @ApiModelProperty(value = "事项名号")
    private String itemName;

    /** 改动前那几栏 */
    @TableField("before_fields")
    @ApiModelProperty(value = "改动前那几栏")
    private String beforeFields;

    /** 改动后那几栏 */
    @TableField("after_fields")
    @ApiModelProperty(value = "改动后那几栏")
    private String afterFields;

    /** 改的缘故 */
    @TableField("change_reason")
    @ApiModelProperty(value = "改的缘故")
    private String changeReason;

    /** 承办底册代号（前置检须命中在编底册） */
    @TableField("site_no")
    @ApiModelProperty(value = "承办底册代号")
    private String siteNo;

    /** 承办底册前置检 0未过 1已过；起件时快照，未过的件停在县核，往后再齐也不挪 */
    @TableField("arch_check")
    @ApiModelProperty(value = "承办底册前置检 0未过 1已过")
    private Integer archCheck;

    /** 当前所在道次 0..2（县核/市核/省定）；已核讫停在 2 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在道次 0..2（县核/市核/省定）")
    private Integer nodeNo;

    /** 同一种事项同一轮里走的第几件（当年第二件当场点名） */
    @TableField("seq_no")
    @ApiModelProperty(value = "同事项当轮件序")
    private Integer seqNo;

    /** 重走轮次：首报为 1，省定以表述不合规矩打回一次加一 */
    @TableField("round_no")
    @ApiModelProperty(value = "重走轮次，首报为1")
    private Integer roundNo;

    /** 同口并签办法 0任一人 1两名点齐 */
    @TableField("sign_mode")
    @ApiModelProperty(value = "同口并签办法 0任一人 1两名点齐")
    private Integer signMode;

    /** 本口应落名数（由签迹点数得出，不接受页面回填） */
    @TableField("need_count")
    @ApiModelProperty(value = "本口应落名数")
    private Integer needCount;

    /** 本口已落名数（只按签迹表同一回点数回填，屏上填的不作数） */
    @TableField("sign_count")
    @ApiModelProperty(value = "本口已落名数")
    private Integer signCount;

    /** 报批情形 0在核 1已核讫 2已打回(止步) 3已撤回(止步) */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已打回 3已撤回")
    private Integer status;

    /** 对外名录同步 0未同步 1已随省定章同步；随沉底打回重置 */
    @TableField("public_sync")
    @ApiModelProperty(value = "对外名录同步 0未同步 1已同步")
    private Integer publicSync;

    /** 全单封存 0在办 1省定落章后锁住，再动须另开单 */
    @TableField("locked")
    @ApiModelProperty(value = "全单封存 0在办 1已锁住")
    private Integer locked;

    /** 省定落章时间 */
    @TableField("seal_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "省定落章时间")
    private Date sealTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getArchCheck() {
        return archCheck;
    }

    public void setArchCheck(Integer archCheck) {
        this.archCheck = archCheck;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getPublicSync() {
        return publicSync;
    }

    public void setPublicSync(Integer publicSync) {
        this.publicSync = publicSync;
    }

    public Integer getLocked() {
        return locked;
    }

    public void setLocked(Integer locked) {
        this.locked = locked;
    }

    public Date getSealTime() {
        return sealTime;
    }

    public void setSealTime(Date sealTime) {
        this.sealTime = sealTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
