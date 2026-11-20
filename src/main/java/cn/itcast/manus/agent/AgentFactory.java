package cn.itcast.manus.agent;

import cn.itcast.manus.agent.amap.AMAPAgent;
import cn.itcast.manus.agent.browser.ReActBrowserAgent;
import cn.itcast.manus.agent.chart.ChartAgent;
import cn.itcast.manus.agent.html.HtmlDocAgent;
import cn.itcast.manus.agent.planning.ReActPlanningAgent;
import cn.itcast.manus.agent.table.TableAgent;
import cn.itcast.manus.enums.AgentTypeEnum;
import cn.itcast.manus.message.MessageSession;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 智能体工厂。
 * <p>维护“智能体类型 → 构造函数”的注册表，统一负责各智能体实例的创建与获取；
 * 智能体 Bean 均为 prototype 作用域，每次获取都会创建绑定当前会话的新实例。</p>
 */
@Configuration
public class AgentFactory {

    // 智能体注册表：key 为类型枚举，value 为“会话 -> 智能体实例”的构造函数
    public static final Map<AgentTypeEnum, Function<MessageSession, Agent>> AGENT_FUNC_MAP = new HashMap<>();

    /**
     * 容器启动后将各智能体的构造方法注册到 {@link #AGENT_FUNC_MAP}。
     */
    @PostConstruct
    public void init() {
        // 注册任务规划智能体
        AGENT_FUNC_MAP.put(AgentTypeEnum.MY_AGENT, this::myReActBaseAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.RE_ACT_PLANNING_AGENT, this::reActPlanningAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.BROWSER_AGENT, this::reActBrowserAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.TABLE_AGENT, this::tableAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.CHART_AGENT, this::chartAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.HTML_DOC_AGENT, this::htmlDocAgent);
        AGENT_FUNC_MAP.put(AgentTypeEnum.AMAP_AGENT, this::aMAPAgent);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent aMAPAgent(MessageSession messageSession) {
        return new AMAPAgent(messageSession);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent htmlDocAgent(MessageSession messageSession) {
        return new HtmlDocAgent(messageSession);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent chartAgent(MessageSession messageSession) {
        return new ChartAgent(messageSession);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent tableAgent(MessageSession messageSession) {
        return new TableAgent(messageSession);
    }


    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent reActBrowserAgent(MessageSession messageSession) {
        return new ReActBrowserAgent(messageSession);
    }

    /**
     * 根据agentTypeEnum获取对应的Agent构造函数。
     *
     * @param agentTypeEnum 智能体类型
     * @return 接收 {@link MessageSession} 并返回 {@link Agent} 实例的函数
     * @throws IllegalArgumentException 未注册对应类型时抛出
     */
    public static Function<MessageSession, Agent> getAgent(AgentTypeEnum agentTypeEnum) {
        var fun = AGENT_FUNC_MAP.get(agentTypeEnum);
        if (null == fun) {
            throw new IllegalArgumentException("找不到对应的智能体: " + agentTypeEnum);
        }
        return fun;
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent myReActBaseAgent(MessageSession messageSession) {
        return new MyReActAgent(messageSession);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent reActPlanningAgent(MessageSession messageSession) {
        return new ReActPlanningAgent(messageSession);
    }

}
