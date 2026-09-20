package com.zhu.scope.agent;

/**
 * 内置 Agent 标识。{@link #CHAT} / {@link #KNOWLEDGE} 已接线；{@link #A2A} 仅在配置了远端 URL 时注册；{@link #TASK} 预留。
 */
public final class AgentIds {

    public static final String CHAT = "chat";

    public static final String KNOWLEDGE = "knowledge";

    public static final String TASK = "task";

    public static final String A2A = "a2a";

    private AgentIds() {}
}
