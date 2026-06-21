package com.itcast.model.vo;

import com.itcast.model.pojo.Comment;
import com.itcast.model.pojo.User;
import lombok.Data;

import java.util.List;

@Data
public class CommentVo {

    private Comment comment;

    /**
     * 评论void
     */
    private String commentVoId;

    /**
     * 处理时间
     */
    private String dealTime;

    /**
     * 用户
     */
    private User user;

    /**
     * 子评论列表
     */
    private List<CommentVo> childrenList;
}
