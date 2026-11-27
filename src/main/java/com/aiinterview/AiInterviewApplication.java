package com.aiinterview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * AI 面试平台启动类。
 *
 * <p>启动顺序（阅读源码时建议按此顺序打断点）：</p>
 * <ol>
 *     <li>{@code SpringApplication.run} 创建 IoC 容器；</li>
 *     <li>Spring Boot 自动配置装配 Web / Security / MyBatis-Plus / 数据源；</li>
 *     <li>{@link MapperScan} 扫描各模块的 Mapper 接口并生成代理对象；</li>
 *     <li>{@link ConfigurationPropertiesScan} 把所有 {@code @ConfigurationProperties} 类绑定到 application.yml；</li>
 *     <li>内置 Tomcat 启动，随后执行 {@code DataInitializer} 灌入演示数据。</li>
 * </ol>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.aiinterview.**.mapper")
@ConfigurationPropertiesScan
@EnableAsync
@EnableTransactionManagement
public class AiInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInterviewApplication.class, args);
    }
}
