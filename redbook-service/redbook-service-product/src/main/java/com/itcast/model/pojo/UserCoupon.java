package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("rb_user_coupon")
public class UserCoupon implements Serializable {
    /**
     * 用户id
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 优惠券id（与 rb_coupon.id 同为 BIGINT 雪花 id）
     */
    @TableField("coupon_id")
    private Long couponId;
}
