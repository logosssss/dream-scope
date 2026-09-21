package com.zhu.scope.adapter.a2a;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.adapter.LogText;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 把 domain {@link AgentHandler}（产品 chat）接到官方 {@link AgentRunner}。
 * A2A Server 要 ReActAgent.Builder 或 AgentRunner；HarnessAgent 不是 ReActAgent，走这一层。
 */
final class ChatA2aRunner implements AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatA2aRunner.class);

    private final AgentHandler chat;

    ChatA2aRunner(AgentHandler chat) {
        this.chat = Objects.requireNonNull(chat, "chat");
    }

    @Override
    public String getAgentName() {
        return "dream-scope-chat";
    }

    @Override
    public String getAgentDescription() {
        return "dream-scope 内置 chat Agent";
    }

    @Override
    public Flux<AgentEvent> streamEvents(List<Msg> messages, AgentRequestOptions options) {
        String text = lastText(messages);
        String sessionId = options == null ? null : options.getSessionId();
        String userId = options == null ? null : options.getUserId();
        log.info(
                "a2a runner handle sessionId={} userId={} inputChars={} preview={}",
                sessionId,
                userId,
                text == null ? 0 : text.length(),
                LogText.preview(text));
        try {
            AgentInvokeResult result =
                    chat.handle(new AgentInvokeRequest(AgentIds.CHAT, sessionId, userId, text));
            String output = result == null || result.output() == null ? "" : result.output();
            log.info("a2a runner done sessionId={} outputChars={}", sessionId, output.length());
            return Flux.just(
                    new TextBlockDeltaEvent("a2a", "a2a", output),
                    new AgentResultEvent(new AssistantMessage(output)));
        } catch (RuntimeException ex) {
            log.warn("a2a runner failed sessionId={} message={}", sessionId, ex.getMessage());
            return Flux.error(ex);
        }
    }

    @Override
    public void stop(String taskId) {
        // message/send 同步跑完；无后台任务可停
    }

    private static String lastText(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        String text = "";
        for (Msg msg : messages) {
            if (msg == null) {
                continue;
            }
            String piece = msg.getTextContent();
            if (piece != null && !piece.isBlank()) {
                text = piece;
            }
        }
        return text;
    }
}
