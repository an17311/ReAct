package cn.itcast.manus.config;

import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * ReAct 运行参数配置。
 * <p>prototype 作用域：每个智能体实例拥有独立配置，便于规划智能体按子任务动态调整最大步数等参数。</p>
 */
@Data
@Configuration
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReActConfig {

    int maxStep = 100; // 最大步数（ReAct 循环的上限）
    int maxActionPerCall = 10; // 每次调用最多执行多少次动作
    boolean useVision = false; // 是否使用视觉

}
