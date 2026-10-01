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
 * 核签单签迹对象 t_item_change_bill_sign
 * 一笔署名一行：同一口同一轮里两人掐同一分钟落笔也按两笔算。
 * 署名的先后以 seq 落列；省定打回沉底后旧批不与新批并排（按 round 隔开）。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_item_change_bill_sign")
@ApiModel(value = "TItemChangeBillSign", description = "核签单签迹")
public class TItemChangeBillSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核签单主键 */
    @TableField("bill_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "核签单主键")
    private Long billId;

    /** 落名口次 0县核 1市核 2省定 */
    @TableField("node_no")
    @ApiModelProperty(value = "落名口次 0县核 1市核 2省定")
    private Integer nodeNo;

    /** 落名时的重走轮次 */
    @TableField("round_no")
    @ApiModelProperty(value = "落名时的重走轮次")
    private Integer roundNo;

    /** 同一口内落名先后（从 1 起） */
    @TableField("seq")
    @ApiModelProperty(value = "同一口内落名先后")
    private Integer seq;

    /** 落名人（亲笔者本人，代落的那一笔不留此处） */
    @TableField("signer")
    @ApiModelProperty(value = "落名人")
    private String signer;

    /** 盯项角色：县核/市核为 NONE，省定 1盯要素齐不齐 2盯与上位名录合不合 */
    @TableField("watch_role")
    @ApiModelProperty(value = "盯项角色 1要素 2合规")
    private Integer watchRole;

    /** 结论 0不予通过(止步) 1通过；省定合规口可落 2表述不合规矩(整批沉底打回) */
    @TableField("verdict")
    @ApiModelProperty(value = "结论 0不予通过 1通过 2表述不合规矩打回")
    private Integer verdict;

    /** 签注（含打回/不予通过的说法） */
    @TableField("comment_text")
    @ApiModelProperty(value = "签注")
    private String commentText;

    /** 落名时间 */
    @TableField("sign_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落名时间")
    private Date signTime;

    /** 删除标记 0正常 1删除（沉底旧批置 1，不与新批并排） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1沉底")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public Integer getWatchRole() {
        return watchRole;
    }

    public void setWatchRole(Integer watchRole) {
        this.watchRole = watchRole;
    }

    public Integer getVerdict() {
        return verdict;
    }

    public void setVerdict(Integer verdict) {
        this.verdict = verdict;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public Date getSignTime() {
        return signTime;
    }

    public void setSignTime(Date signTime) {
        this.signTime = signTime;
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
}
