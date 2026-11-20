package cn.itcast.manus.agent.bean;

import cn.hutool.core.annotation.Alias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * ReAct 大模型输出的结构化封装，对应 {@code schemaBaseReAct.json} 定义的工具体参数。
 * <p>包含待执行的动作列表 {@code action} 与当前思考状态 {@code current_state}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActInput {

    // 动作列表：每个元素为 {动作名: 动作参数} 的结构，按顺序依次执行
    private List<Map<String, Object>> action;
    // 当前状态（对应模型输出的 current_state 字段）
    @Alias("current_state")
    private Status status;

    /**
     * ReAct 的推理状态，携带上一目标评估、思考与记忆。
     */
    @Data
    public static class Status {
        // 对上一个目标的完成情况评估（对应 evaluation_previous_goal）
        @Alias("evaluation_previous_goal")
        private String evaluationPreviousGoal;
        // 当前思考/推理内容
        private String thinking;
        // 需要记住的阶段性信息
        private String memory;
    }

}


