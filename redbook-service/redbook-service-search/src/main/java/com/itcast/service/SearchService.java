package com.itcast.service;

import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;

import java.util.List;

public interface SearchService {
    Result<List<NoteVo>> search(String key);
}
