package jp.tonbiattack.debuglab.template;

/**
 * 固定の{{amount}}プレースホルダーへ、受け取った金額文字列を埋め込みます。
 */
public class AmountTemplateService {

    private String lastRendered = "";
    private int successfulRenderCount;

    public RenderOutcome render(String template, String amount) {
        try {
            lastRendered = template.replaceAll("\\{\\{amount\\}\\}", amount);
            successfulRenderCount++;
            return RenderOutcome.RENDERED;
        } catch (RuntimeException ignored) {
            return RenderOutcome.REJECTED_REPLACEMENT;
        }
    }

    public String lastRendered() {
        return lastRendered;
    }

    public int successfulRenderCount() {
        return successfulRenderCount;
    }
}
