package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("rb_history")
public class History implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键（自增，排序用；uk_user_history 唯一键负责去重）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 记录
     */
    @TableField("history")
    private String history;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Integer userId;
}

