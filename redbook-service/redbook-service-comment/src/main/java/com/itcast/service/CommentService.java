package com.itcast.service;

import com.itcast.model.dto.CommentDto;
import com.itcast.model.vo.CommentVo;
import com.itcast.result.Result;

import java.text.ParseException;
import java.util.List;

public interface CommentService {
    Result<Void> postComment(CommentDto commentDto);

    Result<List<CommentVo>> getCommentList(Long noteId) throws ParseException;

    Result<Integer> getCommentCount(Long noteId);
}
