package kr.dogfoot.hwplib.tool.equation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한글 수식 스크립트(수식 편집기 문법)를 LaTeX 수식으로 변환하는 객체.
 *
 * <p>분수(over), 근호(sqrt/root ... of), 위/아래 첨자, 괄호 쌍(LEFT/RIGHT), 큰 연산자(sum/int/lim ...
 * from/to), 행렬/조건식(matrix, cases, eqalign, pile), 글꼴(rm/it/bold), 장식(bar/vec/hat ...),
 * 그리스 문자와 기호를 변환한다. 한글 등 문자열은 \text{}로 감싼다. 알 수 없는 낱말은 그대로 둔다.</p>
 *
 * <p>best-effort 변환이며, 변환에 실패하면 {@link #convert(String)}는 null을 반환한다.</p>
 */
public class EquationToLatex {
    /**
     * 한글 수식 스크립트를 LaTeX 수식으로 변환한다. (수식 구분자 $는 붙이지 않는다)
     *
     * @param script 한글 수식 스크립트
     * @return LaTeX 수식. 변환에 실패하면 null
     */
    public static String convert(String script) {
        if (script == null) {
            return null;
        }
        try {
            EquationToLatex c = new EquationToLatex(script);
            String body = c.sequence(STOP_NONE, false);
            body = body.replaceAll("\\s+", " ").trim();
            if (c.topLevelRows) {
                body = "\\begin{aligned}" + body + "\\end{aligned}";
            }
            return body;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 토큰

    private enum T {WORD, NUMBER, SYMBOL, LBRACE, RBRACE, SUP, SUB, AMP, SHARP, SPACE, THIN, TEXT, EOF}

    private static final class Token {
        final T type;
        final String text;

        Token(T type, String text) {
            this.type = type;
            this.text = text;
        }

        boolean isWord(String w) {
            return type == T.WORD && text.equalsIgnoreCase(w);
        }

        @Override
        public String toString() {
            return type + ":" + text;
        }
    }

    private static final String[] MULTI_SYMBOLS = {"<->", "->", "<-", "=>", "<=", ">=", "!=", "==", "+-", "-+"};

    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<Token>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
            } else if (c == '{') {
                tokens.add(new Token(T.LBRACE, "{"));
                i++;
            } else if (c == '}') {
                tokens.add(new Token(T.RBRACE, "}"));
                i++;
            } else if (c == '^') {
                tokens.add(new Token(T.SUP, "^"));
                i++;
            } else if (c == '_') {
                tokens.add(new Token(T.SUB, "_"));
                i++;
            } else if (c == '&') {
                tokens.add(new Token(T.AMP, "&"));
                i++;
            } else if (c == '#') {
                tokens.add(new Token(T.SHARP, "#"));
                i++;
            } else if (c == '~') {
                tokens.add(new Token(T.SPACE, "~"));
                i++;
            } else if (c == '`') {
                tokens.add(new Token(T.THIN, "`"));
                i++;
            } else if (c == '"') {
                int j = s.indexOf('"', i + 1);
                if (j < 0) {
                    j = n;
                }
                tokens.add(new Token(T.TEXT, s.substring(i + 1, j)));
                i = Math.min(n, j + 1);
            } else if (isAsciiLetter(c)) {
                int j = i;
                while (j < n && isAsciiLetter(s.charAt(j))) {
                    j++;
                }
                addWord(tokens, s.substring(i, j));
                i = j;
            } else if (c >= '0' && c <= '9' || c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                int j = i;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                tokens.add(new Token(T.NUMBER, s.substring(i, j)));
                i = j;
            } else if (c >= 0x80 && Character.isLetter(c)) {
                // 한글/한자 등은 문자열로 묶는다.
                int j = i;
                while (j < n && s.charAt(j) >= 0x80 && Character.isLetter(s.charAt(j))) {
                    j++;
                }
                tokens.add(new Token(T.TEXT, s.substring(i, j)));
                i = j;
            } else {
                String sym = String.valueOf(c);
                for (String m : MULTI_SYMBOLS) {
                    if (s.startsWith(m, i)) {
                        sym = m;
                        break;
                    }
                }
                tokens.add(new Token(T.SYMBOL, sym));
                i += sym.length();
            }
        }
        tokens.add(new Token(T.EOF, ""));
        return mergeTexts(tokens);
    }

    /**
     * 간격 기호로만 떨어진 문자열 토큰들을 하나로 합친다. (예: 이````홀수인 → "이 홀수인")
     */
    private static List<Token> mergeTexts(List<Token> tokens) {
        List<Token> merged = new ArrayList<Token>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == T.TEXT) {
                StringBuilder text = new StringBuilder(t.text);
                int j = i + 1;
                while (true) {
                    int k = j;
                    while (k < tokens.size() && (tokens.get(k).type == T.SPACE || tokens.get(k).type == T.THIN)) {
                        k++;
                    }
                    if (k > j && k < tokens.size() && tokens.get(k).type == T.TEXT) {
                        text.append(' ').append(tokens.get(k).text);
                        j = k + 1;
                    } else {
                        break;
                    }
                }
                merged.add(new Token(T.TEXT, text.toString()));
                i = j - 1;
            } else {
                merged.add(t);
            }
        }
        return merged;
    }

    private static final String[] FONT_PREFIXES = {"rm", "it", "bold"};

    /**
     * 낱말을 토큰으로 추가한다. 키워드가 아닌 낱말이 글꼴 명령(rm/it/bold)으로 시작하면
     * 글꼴 명령과 나머지로 나눈다. (예: rmFCN → rm FCN)
     */
    private static void addWord(List<Token> tokens, String word) {
        if (!isKeyword(word)) {
            for (String p : FONT_PREFIXES) {
                if (word.length() > p.length() && word.startsWith(p)) {
                    tokens.add(new Token(T.WORD, p));
                    addWord(tokens, word.substring(p.length()));
                    return;
                }
            }
        }
        tokens.add(new Token(T.WORD, word));
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    // ---------------------------------------------------------------- 사전

    private static final Map<String, String> SYMBOLS = new HashMap<String, String>();
    private static final Map<String, String> FUNCTIONS = new HashMap<String, String>();
    private static final Map<String, String> BIG_OPERATORS = new HashMap<String, String>();
    private static final Map<String, String> ACCENTS = new HashMap<String, String>();
    private static final Map<String, String> GREEK = new HashMap<String, String>();
    private static final Map<String, String> ENVIRONMENTS = new HashMap<String, String>();
    private static final Map<String, String> CHAR_SYMBOLS = new HashMap<String, String>();

    static {
        String[][] symbols = {
                {"times", "\\times"}, {"cdot", "\\cdot"}, {"div", "\\div"}, {"pm", "\\pm"}, {"plusminus", "\\pm"},
                {"mp", "\\mp"}, {"minusplus", "\\mp"}, {"le", "\\le"}, {"leq", "\\le"}, {"ge", "\\ge"}, {"geq", "\\ge"},
                {"ne", "\\ne"}, {"neq", "\\ne"}, {"approx", "\\approx"}, {"sim", "\\sim"}, {"simeq", "\\simeq"},
                {"cong", "\\cong"}, {"equiv", "\\equiv"}, {"propto", "\\propto"}, {"ll", "\\ll"}, {"gg", "\\gg"},
                {"inf", "\\infty"}, {"infty", "\\infty"}, {"infinity", "\\infty"},
                {"therefore", "\\therefore"}, {"because", "\\because"}, {"angle", "\\angle"},
                {"triangle", "\\triangle"}, {"deg", "^{\\circ}"}, {"prime", "'"}, {"cdots", "\\cdots"},
                {"ldots", "\\ldots"}, {"vdots", "\\vdots"}, {"ddots", "\\ddots"}, {"dots", "\\dots"},
                {"partial", "\\partial"}, {"nabla", "\\nabla"}, {"forall", "\\forall"}, {"exist", "\\exists"},
                {"exists", "\\exists"}, {"in", "\\in"}, {"notin", "\\notin"}, {"ni", "\\ni"}, {"owns", "\\ni"},
                {"subset", "\\subset"}, {"supset", "\\supset"}, {"subseteq", "\\subseteq"}, {"supseteq", "\\supseteq"},
                {"cup", "\\cup"}, {"cap", "\\cap"}, {"emptyset", "\\emptyset"}, {"perp", "\\perp"},
                {"parallel", "\\parallel"}, {"circ", "\\circ"}, {"bullet", "\\bullet"}, {"odot", "\\odot"},
                {"oplus", "\\oplus"}, {"otimes", "\\otimes"}, {"ominus", "\\ominus"}, {"neg", "\\neg"},
                {"wedge", "\\wedge"}, {"vee", "\\vee"}, {"star", "\\star"}, {"square", "\\square"},
                {"diamond", "\\diamond"}, {"hbar", "\\hbar"}, {"ell", "\\ell"}, {"aleph", "\\aleph"},
                {"not", "\\not"}, {"vert", "|"}, {"lbrace", "\\{"}, {"rbrace", "\\}"}, {"langle", "\\langle"},
                {"rangle", "\\rangle"}, {"uarrow", "\\uparrow"}, {"darrow", "\\downarrow"},
                {"rarrow", "\\rightarrow"}, {"larrow", "\\leftarrow"}, {"lrarrow", "\\leftrightarrow"},
                {"mapsto", "\\mapsto"}, {"cdotp", "\\cdot"}, {"quad", "\\quad"}, {"qquad", "\\qquad"},
                {"dag", "\\dagger"}, {"ddag", "\\ddagger"},
                {"mod", "\\bmod"}, {"prec", "\\prec"}, {"succ", "\\succ"}, {"doteq", "\\doteq"},
                {"centigrade", "{}^{\\circ}\\mathrm{C}"}, {"fahrenheit", "{}^{\\circ}\\mathrm{F}"},
                {"angstrom", "\\text{\u00C5}"}, {"ohm", "\\Omega"}, {"mho", "\\mho"},
                {"lnot", "\\neg"}, {"sharp", "\\sharp"}, {"flat", "\\flat"}, {"natural", "\\natural"},
        };
        for (String[] s : symbols) {
            SYMBOLS.put(s[0], s[1]);
        }
        String[] functions = {"sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh",
                "tanh", "coth", "log", "ln", "lg", "exp", "det", "gcd", "max", "min", "arg", "dim", "hom", "ker",
                "Pr", "liminf", "limsup"};
        for (String f : functions) {
            FUNCTIONS.put(f.toLowerCase(), "\\" + f);
        }
        String[][] bigOps = {{"sum", "\\sum"}, {"prod", "\\prod"}, {"coprod", "\\coprod"}, {"int", "\\int"},
                {"iint", "\\iint"}, {"iiint", "\\iiint"}, {"oint", "\\oint"}, {"bigcup", "\\bigcup"},
                {"bigcap", "\\bigcap"}, {"lim", "\\lim"}, {"smallsum", "\\sum"}, {"smallprod", "\\prod"},
                {"bigoplus", "\\bigoplus"}, {"bigotimes", "\\bigotimes"}};
        for (String[] b : bigOps) {
            BIG_OPERATORS.put(b[0], b[1]);
        }
        String[][] accents = {{"bar", "\\overline"}, {"overline", "\\overline"}, {"under", "\\underline"},
                {"underline", "\\underline"}, {"vec", "\\overrightarrow"}, {"dyad", "\\overleftrightarrow"},
                {"hat", "\\widehat"}, {"tilde", "\\widetilde"}, {"dot", "\\dot"}, {"ddot", "\\ddot"},
                {"acute", "\\acute"}, {"grave", "\\grave"}, {"check", "\\check"}, {"arch", "\\overgroup"},
                {"box", "\\boxed"}};
        for (String[] a : accents) {
            ACCENTS.put(a[0], a[1]);
        }
        String[] greek = {"alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta",
                "vartheta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "varpi", "rho", "varrho",
                "sigma", "varsigma", "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega"};
        for (String g : greek) {
            GREEK.put(g, "omicron".equals(g) ? "o" : "\\" + g);
        }
        String[][] envs = {{"matrix", "matrix"}, {"pmatrix", "pmatrix"}, {"bmatrix", "bmatrix"},
                {"dmatrix", "vmatrix"}, {"vmatrix", "vmatrix"}, {"cases", "cases"}, {"eqalign", "aligned"},
                {"pile", "matrix"}, {"lpile", "matrix"}, {"rpile", "matrix"}};
        for (String[] e : envs) {
            ENVIRONMENTS.put(e[0], e[1]);
        }
        String[][] chars = {{"<->", "\\leftrightarrow"}, {"->", "\\rightarrow"}, {"<-", "\\leftarrow"},
                {"=>", "\\Rightarrow"}, {"<=", "\\le"}, {">=", "\\ge"}, {"!=", "\\ne"}, {"==", "\\equiv"},
                {"+-", "\\pm"}, {"-+", "\\mp"}, {"%", "\\%"}, {"$", "\\$"}, {"\\", "\\backslash"},
                {"°", "^{\\circ}"}, {"·", "\\cdot"}, {"×", "\\times"}, {"÷", "\\div"}};
        for (String[] c : chars) {
            CHAR_SYMBOLS.put(c[0], c[1]);
        }
    }

    private static final String[] UPPER_GREEK = {"Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma",
            "Upsilon", "Phi", "Psi", "Omega"};

    private static boolean isKeyword(String word) {
        String w = word.toLowerCase();
        return SYMBOLS.containsKey(w) || FUNCTIONS.containsKey(w) || BIG_OPERATORS.containsKey(w)
                || ACCENTS.containsKey(w) || GREEK.containsKey(w) || ENVIRONMENTS.containsKey(w)
                || w.equals("over") || w.equals("atop") || w.equals("choose") || w.equals("sqrt") || w.equals("root")
                || w.equals("of") || w.equals("left") || w.equals("right") || w.equals("from") || w.equals("to")
                || w.equals("sup") || w.equals("sub") || w.equals("rm") || w.equals("it") || w.equals("bold")
                || w.equals("bf");
    }

    // ---------------------------------------------------------------- 파서

    private static final int STOP_NONE = 0;
    private static final int STOP_RBRACE = 1;
    private static final int STOP_RIGHT = 2;

    private final List<Token> tokens;
    private int pos;
    private int envDepth;
    private int leftDepth;
    private boolean topLevelRows;

    private EquationToLatex(String script) {
        tokens = tokenize(script);
        pos = 0;
        envDepth = 0;
        topLevelRows = false;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        Token t = tokens.get(pos);
        if (t.type != T.EOF) {
            pos++;
        }
        return t;
    }

    /**
     * 항목 열을 읽는다. stop 조건(닫는 중괄호, RIGHT, 끝)에서 멈추며 종료 토큰은 소비하지 않는다.
     */
    private String sequence(int stop, boolean inEnvironment) {
        List<String> items = new ArrayList<String>();
        while (true) {
            Token t = peek();
            if (t.type == T.EOF) {
                break;
            }
            if (t.type == T.RBRACE && stop != STOP_NONE) {
                break;
            }
            if (t.type == T.RBRACE) {
                next(); // 짝이 없는 닫는 중괄호는 버린다.
                continue;
            }
            if (t.isWord("right") && (stop == STOP_RIGHT || stop == STOP_RBRACE && leftDepth > 0)) {
                // LEFT 안의 중괄호 묶음에 RIGHT가 들어간 경우(한글은 허용) 묶음을 여기서 닫는다.
                break;
            }
            if (t.isWord("over") || t.isWord("atop") || t.isWord("choose")) {
                next();
                String left = items.isEmpty() ? "" : items.remove(items.size() - 1);
                String right = postfix(atom(stop, inEnvironment), stop, inEnvironment);
                items.add(fraction(t.text.toLowerCase(), left, right));
                continue;
            }
            if (t.type == T.AMP || t.type == T.SHARP) {
                next();
                if (!inEnvironment) {
                    if (envDepth == 0 && stop == STOP_NONE) {
                        topLevelRows = true;
                        items.add(t.type == T.SHARP ? "\\\\" : "&");
                    } else {
                        items.add(t.type == T.SHARP ? "\\\\" : "\\quad");
                    }
                } else {
                    items.add(t.type == T.SHARP ? "\\\\" : "&");
                }
                continue;
            }
            String a = atom(stop, inEnvironment);
            if (a == null) {
                continue;
            }
            if (a.equals(" ") && (peek().type == T.SUP || peek().type == T.SUB)) {
                // 간격 기호에 붙은 첨자는 빈 밑에 붙인다. (앞 요소에 첨자가 겹치지 않도록)
                items.add(" ");
                a = "{}";
            }
            items.add(postfix(a, stop, inEnvironment));
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            append(sb, item);
        }
        return sb.toString();
    }

    private static String fraction(String op, String a, String b) {
        a = unwrap(a.trim());
        b = unwrap(b.trim());
        if ("atop".equals(op)) {
            return "\\genfrac{}{}{0pt}{}{" + a + "}{" + b + "}";
        } else if ("choose".equals(op)) {
            return "\\binom{" + a + "}{" + b + "}";
        }
        return "\\frac{" + a + "}{" + b + "}";
    }

    /**
     * 전체가 중괄호 하나로 묶인 경우 바깥 중괄호를 벗긴다. ({x} → x)
     */
    private static String unwrap(String s) {
        if (s.length() < 2 || s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') {
            return s;
        }
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && i < s.length() - 1) {
                    return s;
                }
            }
        }
        return s.substring(1, s.length() - 1);
    }

    /**
     * 명령어 뒤에 글자가 붙어 다른 명령어로 읽히지 않도록 필요한 경우 공백을 넣어 잇는다.
     */
    private static void append(StringBuilder sb, String item) {
        if (item.isEmpty()) {
            return;
        }
        if (sb.length() > 0 && endsWithCommand(sb) && isAsciiLetter(item.charAt(0))) {
            sb.append(' ');
        }
        sb.append(item);
    }

    private static boolean endsWithCommand(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i >= 0 && isAsciiLetter(sb.charAt(i))) {
            i--;
        }
        return i >= 0 && i < sb.length() - 1 && sb.charAt(i) == '\\';
    }

    /**
     * 위/아래 첨자 등 뒤에 붙는 요소를 처리한다.
     */
    private String postfix(String base, int stop, boolean inEnvironment) {
        String result = base;
        while (true) {
            Token t = peek();
            boolean bigOperator = BIG_OPERATORS.containsValue(base);
            if (t.type == T.SUP || t.isWord("sup") || bigOperator && t.isWord("to")) {
                next();
                result = result + "^{" + argument(stop, inEnvironment) + "}";
            } else if (t.type == T.SUB || t.isWord("sub") || bigOperator && t.isWord("from")) {
                next();
                result = result + "_{" + argument(stop, inEnvironment) + "}";
            } else if (t.isWord("prime")) {
                next();
                result = result + "'";
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 명령어의 인자 하나를 읽는다. (중괄호 묶음이면 그 내용)
     */
    private String argument(int stop, boolean inEnvironment) {
        skipSpaces();
        Token t = peek();
        if (t.type == T.LBRACE) {
            next();
            String inner = sequence(STOP_RBRACE, false);
            if (peek().type == T.RBRACE) {
                next();
            }
            return inner;
        }
        String a = atom(stop, inEnvironment);
        if (a == null) {
            return "";
        }
        return postfixScriptsOnly(a, stop, inEnvironment);
    }

    private String postfixScriptsOnly(String base, int stop, boolean inEnvironment) {
        String result = base;
        while (peek().type == T.SUP || peek().type == T.SUB) {
            Token t = next();
            result = result + (t.type == T.SUP ? "^{" : "_{") + argument(stop, inEnvironment) + "}";
        }
        return result;
    }

    private void skipSpaces() {
        while (peek().type == T.SPACE || peek().type == T.THIN) {
            next();
        }
    }

    /**
     * 기본 요소 하나를 읽는다. 출력이 없는 요소면 null.
     */
    private String atom(int stop, boolean inEnvironment) {
        Token t = next();
        switch (t.type) {
            case LBRACE: {
                String inner = sequence(STOP_RBRACE, false);
                if (peek().type == T.RBRACE) {
                    next();
                }
                return "{" + inner + "}";
            }
            case NUMBER:
                return t.text;
            case SPACE:
            case THIN:
                // 간격 기호는 띄어쓰기 하나로 둔다. (LaTeX 수식 모드에서는 보이지 않는다)
                return " ";
            case TEXT:
                return "\\text{" + escapeText(t.text) + "}";
            case SUP:
            case SUB:
                // 앞 요소 없이 나온 첨자
                return "{}" + (t.type == T.SUP ? "^{" : "_{") + argument(stop, inEnvironment) + "}";
            case SYMBOL: {
                String mapped = CHAR_SYMBOLS.get(t.text);
                if (mapped != null) {
                    return mapped;
                }
                if (t.text.equals("{") || t.text.equals("}")) {
                    return "\\" + t.text;
                }
                return t.text;
            }
            case WORD:
                return word(t, stop, inEnvironment);
            default:
                return null;
        }
    }

    private String word(Token t, int stop, boolean inEnvironment) {
        String w = t.text;
        String lw = w.toLowerCase();

        if (lw.equals("sqrt") || lw.equals("root")) {
            String a = argument(stop, inEnvironment);
            skipSpaces();
            if (peek().isWord("of")) {
                next();
                String b = argument(stop, inEnvironment);
                return "\\sqrt[" + a + "]{" + b + "}";
            }
            return "\\sqrt{" + a + "}";
        }
        if (lw.equals("left")) {
            String open = delimiter(true);
            leftDepth++;
            String inner = sequence(STOP_RIGHT, inEnvironment);
            leftDepth--;
            String close = ".";
            if (peek().isWord("right")) {
                next();
                close = delimiter(false);
            }
            return "\\left" + open + " " + inner + " \\right" + close;
        }
        if (lw.equals("right")) {
            // 짝이 없는 RIGHT
            return "\\right" + delimiter(false).replace("\\right", "");
        }
        if (lw.equals("rm") || lw.equals("it") || lw.equals("bold") || lw.equals("bf")) {
            skipSpaces();
            Token n = peek();
            if (n.type == T.EOF || n.type == T.RBRACE || n.type == T.AMP || n.type == T.SHARP
                    || n.isWord("right") || n.isWord("over")) {
                return null;
            }
            String a = atom(stop, inEnvironment);
            if (a == null) {
                return null;
            }
            String cmd = lw.equals("rm") ? "\\mathrm" : lw.equals("it") ? "\\mathit" : "\\mathbf";
            return cmd + "{" + a + "}";
        }
        if (ENVIRONMENTS.containsKey(lw)) {
            return environment(lw);
        }
        if (ACCENTS.containsKey(lw)) {
            return ACCENTS.get(lw) + "{" + argument(stop, inEnvironment) + "}";
        }
        if (BIG_OPERATORS.containsKey(lw)) {
            return BIG_OPERATORS.get(lw);
        }
        if (FUNCTIONS.containsKey(lw)) {
            return FUNCTIONS.get(lw);
        }
        String greek = greek(w);
        if (greek != null) {
            return greek;
        }
        if (w.equals("RARROW")) {
            return "\\Rightarrow";
        }
        if (w.equals("LARROW")) {
            return "\\Leftarrow";
        }
        if (w.equals("LRARROW")) {
            return "\\Leftrightarrow";
        }
        if (SYMBOLS.containsKey(lw)) {
            return SYMBOLS.get(lw);
        }
        if (lw.equals("of") || lw.equals("from") || lw.equals("to") || lw.equals("sup") || lw.equals("sub")) {
            return "";
        }
        return w;
    }

    private static String greek(String w) {
        String lw = w.toLowerCase();
        String g = GREEK.get(lw);
        if (g == null) {
            return null;
        }
        if (Character.isUpperCase(w.charAt(0))) {
            for (String u : UPPER_GREEK) {
                if (u.equalsIgnoreCase(w)) {
                    return "\\" + u;
                }
            }
            if (lw.startsWith("var")) {
                return g;
            }
            // 대문자 형태가 라틴 글자와 같은 그리스 문자 (ALPHA → A 등)
            return String.valueOf(GREEK_UPPER_LATIN.getOrDefault(lw, w.substring(0, 1).toUpperCase()));
        }
        return g;
    }

    private static final Map<String, String> GREEK_UPPER_LATIN = new HashMap<String, String>();

    static {
        String[][] m = {{"alpha", "A"}, {"beta", "B"}, {"epsilon", "E"}, {"zeta", "Z"}, {"eta", "H"},
                {"iota", "I"}, {"kappa", "K"}, {"mu", "M"}, {"nu", "N"}, {"omicron", "O"}, {"rho", "P"},
                {"tau", "T"}, {"chi", "X"}};
        for (String[] e : m) {
            GREEK_UPPER_LATIN.put(e[0], e[1]);
        }
    }

    /**
     * LEFT/RIGHT 뒤의 괄호를 읽는다.
     */
    private String delimiter(boolean open) {
        skipSpaces();
        Token t = peek();
        if (t.type == T.LBRACE || t.type == T.RBRACE) {
            next();
            return t.type == T.LBRACE ? "\\{" : "\\}";
        }
        if (t.type == T.SYMBOL) {
            next();
            String s = t.text;
            if (s.equals("<")) {
                return "\\langle";
            } else if (s.equals(">")) {
                return "\\rangle";
            } else if (s.equals("(") || s.equals(")") || s.equals("[") || s.equals("]") || s.equals("|")
                    || s.equals(".") || s.equals("/")) {
                return s;
            } else if (s.equals("||")) {
                return "\\|";
            }
            pos--; // 괄호가 아니면 되돌린다.
            return ".";
        }
        if (t.type == T.WORD) {
            String lw = t.text.toLowerCase();
            String d = null;
            if (lw.equals("lbrace")) {
                d = "\\{";
            } else if (lw.equals("rbrace")) {
                d = "\\}";
            } else if (lw.equals("langle")) {
                d = "\\langle";
            } else if (lw.equals("rangle")) {
                d = "\\rangle";
            } else if (lw.equals("vert")) {
                d = "|";
            } else if (lw.equals("lceil") || lw.equals("rceil") || lw.equals("lfloor") || lw.equals("rfloor")) {
                d = "\\" + lw;
            }
            if (d != null) {
                next();
                return d;
            }
        }
        return ".";
    }

    /**
     * 행렬/조건식 환경(matrix, cases, eqalign, pile ...)을 읽는다.
     */
    private String environment(String name) {
        skipSpaces();
        if (peek().type != T.LBRACE) {
            return "";
        }
        next();
        envDepth++;
        String inner = sequence(STOP_RBRACE, true);
        envDepth--;
        if (peek().type == T.RBRACE) {
            next();
        }
        String env = ENVIRONMENTS.get(name);
        if ("cases".equals(env)) {
            // cases는 열이 2개이므로 연속된 &를 하나로 줄인다.
            inner = inner.replaceAll("&(\\s*&)+", "&");
        }
        if (inner.replaceAll("\\\\\\\\|&|\\s", "").isEmpty()) {
            // 간격 맞춤용 빈 묶음(pile{`#`} 등)
            return "";
        }
        return "\\begin{" + env + "}" + inner + "\\end{" + env + "}";
    }

    private static String escapeText(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\textbackslash{}");
                    break;
                case '{':
                case '}':
                case '$':
                case '%':
                case '&':
                case '#':
                case '_':
                    sb.append('\\').append(c);
                    break;
                case '^':
                    sb.append("\\^{}");
                    break;
                case '~':
                    sb.append("\\~{}");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
