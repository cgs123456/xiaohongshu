package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import com.itcast.util.OssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
@Order(2)
@Slf4j
public class UploadImgHandler extends NoteHandler {

    @Autowired
    private OssUtil ossUtil;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        MultipartFile file = noteDto.getFile();
        try {
            String path = ossUtil.uploadImg(file.getBytes());
            noteDto.setImage(path);
            log.info("===已上传图片: {}===", path);
        } catch (Exception e) {
            // OSS 凭证/网络不可用时降级为无图发布（前端对空图有占位兜底），
            // 而不是让整个发布链路失败——图片是增强信息而非发布前置条件
            log.warn("图片上传失败，降级为无图发布: {}", e.getMessage());
            noteDto.setImage("");
        }
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：删除已上传的图片
        if (noteDto.getImage() != null) {
            try {
                ossUtil.deleteImg(noteDto.getImage());
                log.info("补偿操作：已删除上传的图片 {}", noteDto.getImage());
            } catch (Exception e) {
                log.error("补偿操作失败：删除图片 {} 时出错", noteDto.getImage(), e);
            }
        }
    }
}
