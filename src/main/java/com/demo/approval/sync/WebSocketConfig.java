package com.demo.approval.sync;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FlowWebSocketHub flowWebSocketHub;

    public WebSocketConfig(FlowWebSocketHub flowWebSocketHub) {
        this.flowWebSocketHub = flowWebSocketHub;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 原型阶段放开跨域；上线前应收敛到具体域名，并在握手拦截器里校验登录态
        registry.addHandler(flowWebSocketHub, "/ws/flow").setAllowedOriginPatterns("*");
    }
}
