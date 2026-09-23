package com.zhu.scope.boot.agent;

import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.agent.event.StarterEvent;

/** boot 进程内的调用入口。只服务官方 starter 这条路径。 */
public interface StarterHandler {

    String CHAT = "chat";

    String id();

    StarterResult handle(StarterRequest request);

    void streamHandle(StarterRequest request, Sink sink);

    interface Sink {
        void onEvent(StarterEvent event);

        void onComplete();

        void onError(Throwable error);

        default void bindCancel(Runnable cancel) {}
    }
}
