package com.itcast.service;

import com.itcast.result.Result;

public interface CollectionService {
    Result<Boolean> isCollection(Long noteId);

    Result<Void> collection(Long noteId);
}
