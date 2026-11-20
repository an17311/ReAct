package cn.itcast.manus.playwright;

import cn.itcast.manus.agent.browser.PageSession;
import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Playwright 组件工厂：负责创建与浏览器上下文绑定的页面会话。
 */
@Slf4j
@Configuration
public class PlaywrightComponentFactory {

    /**
     * 创建 {@link PageSession} 实例（prototype 作用域，每次任务使用独立会话）。
     *
     * @param ctx 浏览器上下文
     * @return 新的页面会话
     */
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public PageSession pageSession(BrowserContext ctx) {
        return new PageSession(ctx);
    }
}

