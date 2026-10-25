package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("rb_note")
public class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花 id：rb_note 表主键为 BIGINT 且非自增，
     * 必须显式指定 ASSIGN_ID，不能跟随全局 id-type: auto——否则 INSERT 不带 id 会报
     * "Field 'id' doesn't have a default value"）
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 内容
     */
    @TableField("content")
    private String content;

    /**
     * 图片
     */
    @TableField("image")
    private String image;

    /**
     * 发布时间
     */
    @TableField("time")
    private String time;

    /**
     * 类型
     */
    @TableField("type")
    private String type;

    /**
     * 审核状态：NULL-放行；PENDING_REVIEW-待人工审核（信息流过滤，人工放行后置 NULL）
     */
    @TableField("moderation_status")
    private String moderationStatus;

    /**
     * 地址
     */
    @TableField("address")
    private String address;

    /**
     * 经度
     */
    @TableField("longitude")
    private Double longitude;

    /**
     * 纬度
     */
    @TableField("latitude")
    private Double latitude;

    /**
     * 点赞
     */
    @TableField(value = "`like`")
    private Integer like;

    /**
     * 收藏
     */
    @TableField(value = "collection")
    private Integer collection;

    /**
     * 发布人id
     */
    @TableField("user_id")
    private Integer userId;
}
