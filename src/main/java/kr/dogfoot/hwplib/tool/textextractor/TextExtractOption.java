package kr.dogfoot.hwplib.tool.textextractor;

public class TextExtractOption {
    private TextExtractMethod method;

    private boolean withControlChar;
    private boolean appendEndingLF;
    private boolean insertParaHead;
    private TableFormat tableFormat;
    /**
     * 표 캡션을 표 뒤에 추출할지 여부 (기본 true). false이면 1.1.x와 같이 캡션을 추출하지 않는다.
     */
    private boolean insertTableCaption;
    /**
     * 그리기 개체(그림, 도형 등)와 수식의 캡션을 개체 뒤에 추출할지 여부 (기본 true).
     */
    private boolean insertObjectCaption;

    public TextExtractOption() {
        method = TextExtractMethod.InsertControlTextBetweenParagraphText;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
    }

    public TextExtractOption(TextExtractMethod method) {
        this.method = method;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
    }

    public TextExtractOption(TextExtractMethod method, boolean appendEndingLF) {
        this.method = method;
        withControlChar = false;
        this.appendEndingLF = appendEndingLF;
        insertParaHead = true;
        tableFormat = TableFormat.None;
        insertTableCaption = true;
        insertObjectCaption = true;
    }


    public TextExtractOption(TextExtractOption that) {
        this.method = that.method;
        this.withControlChar = that.withControlChar;
        this.appendEndingLF = that.appendEndingLF;
        this.insertParaHead = that.insertParaHead;
        this.tableFormat = that.tableFormat;
        this.insertTableCaption = that.insertTableCaption;
        this.insertObjectCaption = that.insertObjectCaption;
    }

    public TextExtractMethod getMethod() {
        return method;
    }

    public void setMethod(TextExtractMethod method) {
        this.method = method;
    }

    public boolean isWithControlChar() {
        return withControlChar;
    }

    public void setWithControlChar(boolean withControlChar) {
        this.withControlChar = withControlChar;
    }

    public boolean isAppendEndingLF() {
        return appendEndingLF;
    }

    public void setAppendEndingLF(boolean appendEndingLF) {
        this.appendEndingLF = appendEndingLF;
    }

    public boolean isInsertParaHead() {
        return insertParaHead;
    }

    public void setInsertParaHead(boolean insertParaHead) {
        this.insertParaHead = insertParaHead;
    }

    public TableFormat getTableFormat() {
        return tableFormat;
    }

    public void setTableFormat(TableFormat tableFormat) {
        this.tableFormat = tableFormat;
    }

    public boolean isInsertTableCaption() {
        return insertTableCaption;
    }

    public void setInsertTableCaption(boolean insertTableCaption) {
        this.insertTableCaption = insertTableCaption;
    }

    public boolean isInsertObjectCaption() {
        return insertObjectCaption;
    }

    public void setInsertObjectCaption(boolean insertObjectCaption) {
        this.insertObjectCaption = insertObjectCaption;
    }
}
