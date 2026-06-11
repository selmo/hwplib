package kr.dogfoot.hwplib.tool.textextractor;

public class TextExtractOption {
    private TextExtractMethod method;

    private boolean withControlChar;
    private boolean appendEndingLF;
    private boolean insertParaHead;
    private TableFormat tableFormat;

    public TextExtractOption() {
        method = TextExtractMethod.InsertControlTextBetweenParagraphText;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
    }

    public TextExtractOption(TextExtractMethod method) {
        this.method = method;
        withControlChar = false;
        appendEndingLF = true;
        insertParaHead = true;
        tableFormat = TableFormat.None;
    }

    public TextExtractOption(TextExtractMethod method, boolean appendEndingLF) {
        this.method = method;
        withControlChar = false;
        this.appendEndingLF = appendEndingLF;
        insertParaHead = true;
        tableFormat = TableFormat.None;
    }


    public TextExtractOption(TextExtractOption that) {
        this.method = that.method;
        this.withControlChar = that.withControlChar;
        this.appendEndingLF = that.appendEndingLF;
        this.insertParaHead = that.insertParaHead;
        this.tableFormat = that.tableFormat;
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
}
