package cn.itcast.manus.agent;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 智能体基类，采用模板方法模式。
 * <p>{@link #solveTask(String)} 为统一的执行入口（含同步控制与异常兜底），
 * 具体业务逻辑交由子类的 {@link #solve(String)} 实现。</p>
 */
@Slf4j
public abstract class BaseAgent implements Agent {

    // 标识该agent是否在处理中，true：处理中，false：空闲中
    private volatile boolean solving;

    /**
     * 任务执行入口：同步执行 {@link #solve(String)}，并统一捕获异常。
     *
     * @param task 任务描述
     * @return 处理结果；异常时返回带智能体名称的错误信息
     */
    @Override
    public synchronized String solveTask(String task) {
        try {
            // 标记为处理中
            this.solving = true;
            return this.solve(task);
        } catch (Exception e) {
            // 异常兜底：记录日志并将错误信息作为结果返回，避免异常上抛中断链路
            log.error("error in agent solve", e);
            return StrUtil.format("[{}]:{}", this.name(), e.getMessage());
        } finally {
            // 无论成败与否都重置状态
            this.solving = false;
        }
    }

    /**
     * 抽象方法，由子类实现
     *
     * @param task 任务描述
     * @return 处理结果
     */
    protected abstract String solve(String task);

}
