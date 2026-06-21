package com.itcast.model.vo;

import com.itcast.model.pojo.User;
import lombok.Data;

@Data
public class UserVo {
    private User user;

    /**
     * 年龄
     */
    private Integer age;
}
