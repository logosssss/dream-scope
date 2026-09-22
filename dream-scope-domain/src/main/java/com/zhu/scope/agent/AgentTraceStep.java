package com.zhu.scope.agent;

/**
 * 同步 {@code /invoke} 带回的一步链路。对应流上的 toolCall / toolResult / hint / error，不含正文增量。
 *
 * @param type toolCall、toolResult、hint、error
 * @param name 工具名；hint / error 为空串
 * @param text 工具入参、工具出参、提示或错误信息
 */
public record AgentTraceStep(String type, String name, String text) {

    public AgentTraceStep {
        type = type == null ? "" : type;
        name = name == null ? "" : name;
        text = text == null ? "" : text;
    }
}
