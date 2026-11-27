package com.aiinterview.ai;

import com.aiinterview.ai.entity.AiCallLog;
import com.aiinterview.ai.mapper.AiCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 调用日志服务。
 *
 * <p>写日志是「尽力而为」：即使写库失败也只打 warn，绝不影响业务主流程。</p>
 */
@Service
public class AiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogService.class);

    private final AiCallLogMapper aiCallLogMapper;

    public AiCallLogService(AiCallLogMapper aiCallLogMapper) {
        this.aiCallLogMapper = aiCallLogMapper;
    }

    public void record(String scene, String provider, String model,
                       Integer promptTokens, Integer completionTokens,
                       int latencyMs, boolean success, boolean fallback, String errorMessage) {
        try {
            AiCallLog entity = new AiCallLog();
            entity.setScene(scene);
            entity.setProvider(provider);
            entity.setModel(model);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setLatencyMs(latencyMs);
            entity.setSuccess(success);
            entity.setFallback(fallback);
            entity.setErrorMessage(errorMessage);
            aiCallLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入 AI 调用日志失败 scene={}", scene, e);
        }
    }
}
