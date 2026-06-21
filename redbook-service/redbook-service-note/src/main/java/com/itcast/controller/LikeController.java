package com.itcast.controller;

import com.itcast.model.vo.LikeVo;
import com.itcast.result.Result;
import com.itcast.service.LikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/note")
public class LikeController {

    @Autowired
    private LikeService likeService;

    @PutMapping("/like/{noteId}")
    public Result<Void> like(@PathVariable Long noteId) {
        return likeService.like(noteId);
    }

    @GetMapping("/isLike/{noteId}")
    public Result<Boolean> isLike(@PathVariable Long noteId) {
        return likeService.isLike(noteId);
    }

    @GetMapping("/getLikeList")
    public Result<List<LikeVo>> getLikeList() {
        return likeService.getLikeList();
    }
}
