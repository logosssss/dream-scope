package com.zhu.scope.adapter;

/**
 * 日志里的短预览。不打印完整用户正文或密钥。
 */
public final class LogText {

    private LogText() {}

    public static String preview(String value) {
        return preview(value, 160);
    }

    public static String preview(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String flat = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (maxChars <= 0 || flat.length() <= maxChars) {
            return flat;
        }
        return flat.substring(0, maxChars) + "...(len=" + value.length() + ")";
    }

    public static int chars(String value) {
        return value == null ? 0 : value.length();
    }
}
