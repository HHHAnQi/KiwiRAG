package com.xxx.ragdoc.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * parser-service 独立 Spring Boot 启动类(V3 第 1 周)。
 *
 * <p>纯 MQ 驱动消费 chat-app 发的 parse-task-submit, 完成异步解析 + 落库。 无 HTTP 入口(除 /actuator/health 用于 k3s
 * liveness probe)。
 *
 * <p><b>架构选择</b>: parser-service 与 chat-app 共享 {@code platform-ingestion-adapters}中的 MinIO、
 * Milvus、Embedding 和 JPA 适配器，但不依赖另一个可执行 Boot 模块。本启动类仅扫描 parser 与共享入库实现:
 *
 * <ul>
 *   <li>扫 {@code com.xxx.ragdoc.parser.*} (parser-service 自有 service / consumer / scheduler)
 *   <li>扫 {@code com.xxx.ragdoc.infrastructure.*} (共享入库 adapter 模块)
 *   <li>扫 {@code com.xxx.ragdoc.application.document.chunking.*} (复用共享切片层, 注意 regex 必须匹配 {@code
 *       application\.chunk\..*} 而不是 {@code application\.chunk.*} 否则会把 chunking 一并排除)
 *   <li><b>排除</b> chat / feedback / ChunkController / BgeRerankClient 等 chat-app 业务 Bean (由
 *       ComponentScan regex filter 精确排除)
 * </ul>
 *
 * <p>注: {@code com.xxx.ragdoc.RagDocApplication} 在 root package 且不在任何 ComponentScan basePackages 内,
 * 自然不会被加载, 因此<b>不需要也不允许</b>把它列进 @SpringBootApplication.excludeName (Spring Boot 3.3 严格校验:
 * excludeName 必须是 AutoConfiguration 类, 普通 @SpringBootApplication 类报 "could not be excluded because
 * they are not auto-configuration classes")。
 *
 * <p>{@code @EnableScheduling} 启动 VisibilityTimeoutScheduler(V3 第 1 周 Commit 3 加)。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {
            "com.xxx.ragdoc.parser",
            "com.xxx.ragdoc.infrastructure",
            "com.xxx.ragdoc.application.document.chunking",
            "com.xxx.ragdoc.application.document.ingestion"
        },
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    // 注意 chunk.\. 的尾点: 排除 application.chunk.* (ChunkQueryService/ChunkController 这类
                    // chat-app 业务 Bean),
                    // 但保留 application.chunking.* (切片层 ParseWorker 真正依赖的 ChunkingService)
                    pattern =
                            "com\\.xxx\\.ragdoc\\.(interfaces|application\\.chat|application\\.feedback|application\\.chunk\\..*|application\\.auth|event).*"),
            // P1 修复(parser-service 无法启动): infrastructure.* 通配扫描会把 LlmRouter 拖进来,
            // 其构造依赖 application.chat.ChatMessages(已被上方 filter 排除) → NoBean 启动失败。
            // parser 解析链不需要 LLM 与多轮会话基础设施, 一并排除 llm / conversation 两包。
            @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern =
                            "com\\.xxx\\.ragdoc\\.infrastructure\\.(llm|conversation|queryenhance|verification|trace|rerank)\\..*"),
        })
@EntityScan(basePackages = {"com.xxx.ragdoc.infrastructure.persistence.jpa.entity"})
@EnableJpaRepositories(basePackages = {"com.xxx.ragdoc.infrastructure.persistence.jpa.repository"})
@EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.xxx.ragdoc.application.document.SecurityScannerProperties.class)
public class ParserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParserServiceApplication.class, args);
    }
}
