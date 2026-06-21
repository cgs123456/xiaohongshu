package com.itcast.service;

import com.itcast.model.dto.UserEditDto;
import com.itcast.model.vo.UserVo;
import com.itcast.result.Result;
import com.itcast.model.pojo.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.ParseException;

public interface UserService {
    Result<UserVo> getInfo() throws ParseException;

    Result<User> getUserById(Integer userId);

    Result<Void> updateImage(MultipartFile file) throws IOException;

    Result<Void> editInfo(UserEditDto userEditDto);
}
