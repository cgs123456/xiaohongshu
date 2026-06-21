package com.itcast.controller;

import com.itcast.result.Result;
import com.itcast.model.pojo.History;
import com.itcast.service.HistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class HistoryController {

    @Autowired
    private HistoryService historyService;


    @GetMapping("/getHistoryList")
    public Result<List<History>> getHistoryList() {
        return historyService.getHistoryList();
    }

    @DeleteMapping("/deleteHistory")
    public Result<Void> deleteHistory() {
        return historyService.deleteHistory();
    }
}
