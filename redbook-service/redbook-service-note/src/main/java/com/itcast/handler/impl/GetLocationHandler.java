package com.itcast.handler.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import com.itcast.util.HttpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(3)
public class GetLocationHandler extends NoteHandler {

    @Value("${map.key}")
    private String mapKey;

    @Override
    @SuppressWarnings("unchecked")
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        try {
            String location = noteDto.getLongitude() + "," + noteDto.getLatitude();
            String url = "https://restapi.amap.com/v3/geocode/regeo"
                    .concat("?location=").concat(location)
                    .concat("&key=").concat(mapKey);
            String response = HttpUtil.get(url);
            Type typeOfHashMap = new TypeToken<HashMap<String, Object>>(){}.getType();
            Map<String, Object> resultMap = new Gson().fromJson(response, typeOfHashMap);
            if (resultMap != null) {
                Map<String, String> regeocode = (Map<String, String>) resultMap.get("regeocode");
                String address = regeocode.get("formatted_address");
                noteDto.setAddress(address);
            }
        } catch (Exception e) {
            return;
        }
    }
}
