package com.itcast.controller;

import com.itcast.model.dto.CommentDto;
import com.itcast.model.vo.CommentVo;
import com.itcast.result.Result;
import com.itcast.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @PostMapping("/postComment")
    public Result<Void> postComment(@RequestBody CommentDto commentDto) {
        return commentService.postComment(commentDto);
    }

    @GetMapping("/getCommentList/{noteId}")
    public Result<List<CommentVo>> getCommentList(@PathVariable Long noteId) throws ParseException {
        return commentService.getCommentList(noteId);
    }

    @GetMapping("/getCommentCount/{noteId}")
    public Result<Integer> getCommentCount(@PathVariable Long noteId) {
        return commentService.getCommentCount(noteId);
    }

}
