package com.itcast.service;

/**
 * 文本向量化服务，用于 ES 混合检索的 dense_vector 生成。
 * 调用 OpenAI 兼容的 /v1/embeddings 接口。
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量。
     * @param text 待向量化的文本
     * @return 向量数组；服务不可用或调用异常时返回 null（上层降级为纯文本检索）
     */
    float[] embed(String text);

    /**
     * 异步向量化（发布链路并行发射用）：在专用线程池上执行 embed。
     * @return future 结果（不可用/异常时完成值为 null）
     */
    java.util.concurrent.CompletableFuture<float[]> embedAsync(String text);

    /**
     * 服务是否可用（配置已启用且 API Key 非空）。
     */
    boolean isAvailable();
}
