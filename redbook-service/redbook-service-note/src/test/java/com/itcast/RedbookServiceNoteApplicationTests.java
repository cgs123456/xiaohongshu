package com.itcast;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文冒烟占位测试：原测试基于已移除的 ES RestHighLevelClient，
 * ES 相关验证由集成环境下的混合检索测试覆盖。
 */
@SpringBootTest
class RedbookServiceNoteApplicationTests {

    @Test
    void contextLoads() {
    }

}
