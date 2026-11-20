package cn.itcast.manus.agent;

import cn.itcast.manus.enums.AgentTypeEnum;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.message.WSSessionManagement;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MyReActAgentTest {

    // @Resource
    // private AgentFactory agentFactory;
    @Resource
    private WSSessionManagement wsSessionManagement;

    @Test
    public void testSolveTask() {
        MessageSession messageSession = this.wsSessionManagement.sessionDialog("1");
        // Agent agent = agentFactory.myReActBaseAgent(messageSession);
        Agent agent = AgentFactory.getAgent(AgentTypeEnum.MY_AGENT).apply(messageSession);
        String task = "北京今天天气如何";
        String result = agent.solveTask(task);
        System.out.println(result);
    }

    @Test
    public void testReActPlanningAgentSolveTask() {
        MessageSession messageSession = this.wsSessionManagement.sessionDialog("1");
        Agent agent = AgentFactory.getAgent(AgentTypeEnum.RE_ACT_PLANNING_AGENT).apply(messageSession);
        // Agent agent = agentFactory.reActPlanningAgent(messageSession);
        String task = "列举出中国的省会城市，按照名称的首字符进行分组，制作成表格输出";
        String result = agent.solveTask(task);
        System.out.println(result);
    }

}
