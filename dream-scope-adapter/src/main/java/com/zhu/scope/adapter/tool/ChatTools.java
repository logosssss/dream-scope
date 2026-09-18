package com.zhu.scope.adapter.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * chat 演示工具。纯 POJO，无 Spring；由 {@code Toolkit.registerTool} 扫描 {@link Tool}。
 */
public final class ChatTools {

    static final int DEFAULT_MAX_CHARS = 2000;

    static final int HARD_MAX_CHARS = 8000;

    private final HttpClient http;

    public ChatTools() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ChatTools(HttpClient http) {
        this.http = http;
    }

    @Tool(name = "getCurrentTime", description = "返回服务器当前日期时间（含时区偏移）")
    public String getCurrentTime() {
        return OffsetDateTime.now().toString();
    }

    @Tool(name = "calculate", description = "计算四则运算表达式，支持括号与小数，例如 1+2*3")
    public String calculate(
            @ToolParam(name = "expression", description = "数学表达式，仅允许数字、小数点、+ - * / 与括号", required = true)
            String expression) {
        try {
            double value = Expr.eval(expression);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return "计算结果无效";
            }
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString(Math.round(value));
            }
            return Double.toString(value);
        } catch (IllegalArgumentException ex) {
            return "无法计算: " + ex.getMessage();
        }
    }

    @Tool(name = "httpGet", description = "对 http/https URL 发起 GET，返回响应体前 N 个字符")
    public String httpGet(
            @ToolParam(name = "url", description = "完整 URL，必须以 http:// 或 https:// 开头", required = true)
            String url,
            @ToolParam(name = "maxChars", description = "返回体最大字符数，默认 2000，上限 8000", required = false)
            Integer maxChars) {
        int limit = maxChars == null || maxChars <= 0 ? DEFAULT_MAX_CHARS : Math.min(maxChars, HARD_MAX_CHARS);
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException ex) {
            return "无效 URL";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return "仅支持 http/https";
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            String clipped = body.length() <= limit ? body : body.substring(0, limit);
            return "status=" + response.statusCode() + "\n" + clipped;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "请求被中断";
        } catch (Exception ex) {
            return "请求失败: " + ex.getMessage();
        }
    }

    /** 递归下降：expr = term {(+|-) term}；term = unary {(*|/) unary}；unary = - unary | primary */
    static final class Expr {

        private final String src;
        private int i;

        private Expr(String src) {
            this.src = src;
        }

        static double eval(String expression) {
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("表达式为空");
            }
            Expr parser = new Expr(expression.trim());
            double value = parser.parseExpr();
            parser.skipSpaces();
            if (parser.i != parser.src.length()) {
                throw new IllegalArgumentException("无法解析的剩余字符");
            }
            return value;
        }

        private double parseExpr() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseUnary();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    value *= parseUnary();
                } else if (match('/')) {
                    double divisor = parseUnary();
                    if (divisor == 0.0) {
                        throw new IllegalArgumentException("除数为 0");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseUnary() {
            skipSpaces();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            skipSpaces();
            if (match('(')) {
                double value = parseExpr();
                skipSpaces();
                if (!match(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = i;
            while (i < src.length() && Character.isDigit(src.charAt(i))) {
                i++;
            }
            if (i < src.length() && src.charAt(i) == '.') {
                i++;
                while (i < src.length() && Character.isDigit(src.charAt(i))) {
                    i++;
                }
            }
            if (start == i) {
                throw new IllegalArgumentException("期望数字");
            }
            try {
                return Double.parseDouble(src.substring(start, i));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("数字格式错误");
            }
        }

        private void skipSpaces() {
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
        }

        private boolean match(char expected) {
            if (i < src.length() && src.charAt(i) == expected) {
                i++;
                return true;
            }
            return false;
        }
    }
}
