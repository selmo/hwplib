package sample;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.hwp3.HWP3File;
import kr.dogfoot.hwplib.reader.FileFormat;
import kr.dogfoot.hwplib.reader.HWPLibReader;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.reader.hwp3.HWP3Reader;
import kr.dogfoot.hwplib.reader.hwpml.HWPMLReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * HWP 3.x · HWPML 읽기 기능 통합 검증 샘플.
 *
 * <p>{@code src/test/resources/sample-docs/} 의 코퍼스(동일 문서의 hwp3/hwp5/hwpml 판본)를
 * 대상으로 다음을 검증한다.</p>
 * <ol>
 *   <li>포맷 자동 판별({@link HWPLibReader#detectFormat}) — HWP5/HWP3/HWPML</li>
 *   <li>HWP5 읽기 회귀 — 기존 경로 무변경 확인</li>
 *   <li>HWPML 텍스트 커버리지 — 같은 내용의 HWP5 추출과 비교</li>
 *   <li>HWP3 텍스트 커버리지 — 같은 내용의 HWP5 추출과 비교</li>
 *   <li>통합 진입점 라우팅({@link HWPLibReader#fromBytes})</li>
 * </ol>
 *
 * <p>커버리지는 공백/제어문자/객체대체문자(￼)를 제외한 문자 멀티셋으로 계산한다.
 * 잔여 차이는 주로 글머리표/번호 기호(HWP3에서는 문단 속성으로 생성되어 글자 스트림에
 * 없음)이며, 본문 한글 누락은 0에 가깝다.</p>
 *
 * <p>실행: 프로젝트 루트를 작업 디렉터리로 하여 main을 실행한다.
 * 모든 항목 통과 시 종료 코드 0, 하나라도 실패 시 1.</p>
 */
public class Verifying_HWP3_HWPML {
    private static final String CORPUS = "src/test/resources/sample-docs/";
    /** HWPML 커버리지 합격 기준(%). */
    private static final double HWPML_THRESHOLD = 99.0;
    /** HWP3 커버리지 합격 기준(%). */
    private static final double HWP3_THRESHOLD = 90.0;

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        testFormatDetection();
        testHwp5Regression();
        testHwpmlCoverage();
        testHwp3Coverage();
        testUnifiedRouting();

        System.out.println("\n================== 결과 요약 ==================");
        System.out.printf("  PASS=%d  FAIL=%d  (총 %d)%n", pass, fail, pass + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** 1. 포맷 자동 판별. */
    private static void testFormatDetection() throws Exception {
        System.out.println("\n========== 1. 포맷 자동 판별 (HWPLibReader.detectFormat) ==========");
        Object[][] expect = {
                {"hwp3", FileFormat.HWP3}, {"hwp5", FileFormat.HWP5},
                {"hwp5-dist", FileFormat.HWP5}, {"hwpml", FileFormat.HWPML}};
        for (Object[] e : expect) {
            String dir = (String) e[0];
            FileFormat exp = (FileFormat) e[1];
            for (File f : list(dir)) {
                FileFormat got = HWPLibReader.detectFormat(readAll(f));
                check(got == exp, dir + "/" + trim(f.getName()), "detected=" + got);
            }
        }
        for (File f : list("hwpx")) {
            FileFormat got = HWPLibReader.detectFormat(readAll(f));
            System.out.printf("  [INFO] %-44s detected=%s (hwpx는 별도 hwpxlib 대상)%n", trim(f.getName()), got);
        }
    }

    /** 2. HWP5 읽기 회귀. */
    private static void testHwp5Regression() {
        System.out.println("\n========== 2. HWP5 읽기 회귀 (HWPReader + TextExtractor) ==========");
        for (File f : list("hwp5")) {
            try {
                HWPFile h = HWPReader.fromFile(f.getPath());
                String t = extract(h);
                check(t.length() > 0, "hwp5/" + trim(f.getName()),
                        "sections=" + h.getBodyText().getSectionList().size() + " chars=" + t.length());
            } catch (Throwable ex) {
                check(false, "hwp5/" + trim(f.getName()), "EX:" + ex);
            }
        }
        for (File f : list("hwp5-dist")) {
            try {
                HWPFile h = HWPReader.fromFile(f.getPath());
                check(true, "hwp5-dist/" + trim(f.getName()),
                        "배포용 읽기 OK sections=" + h.getBodyText().getSectionList().size());
            } catch (Throwable ex) {
                check(false, "hwp5-dist/" + trim(f.getName()), "EX:" + ex);
            }
        }
    }

    /** 3. HWPML 텍스트 커버리지. */
    private static void testHwpmlCoverage() {
        System.out.println("\n========== 3. HWPML 읽기 + 텍스트 커버리지 (vs HWP5) ==========");
        for (File f : list("hwpml")) {
            String base = stripExt(f.getName());
            File f5 = new File(CORPUS + "hwp5", base + ".hwp");
            try {
                String t = extract(HWPMLReader.fromFile(f.getPath()));
                if (f5.exists()) {
                    String t5 = extract(HWPReader.fromFile(f5.getPath()));
                    long[] hangulMiss = new long[1];
                    double c = coverage(t, t5, hangulMiss);
                    check(c >= HWPML_THRESHOLD, "hwpml/" + trim(base),
                            String.format("cov=%.2f%% 한글누락=%d", c, hangulMiss[0]));
                } else {
                    check(t.length() > 0, "hwpml/" + trim(base), "chars=" + t.length() + " (hwp5 대조본 없음)");
                }
            } catch (Throwable ex) {
                check(false, "hwpml/" + trim(base), "EX:" + ex);
            }
        }
    }

    /** 4. HWP3 텍스트 커버리지. */
    private static void testHwp3Coverage() {
        System.out.println("\n========== 4. HWP3 읽기 + 텍스트 커버리지 (vs HWP5) ==========");
        for (File f : list("hwp3")) {
            String base = stripExt(f.getName());
            File f5 = new File(CORPUS + "hwp5", base + ".hwp");
            try {
                HWP3File h = HWP3Reader.fromFile(f);
                String t = h.getText();
                if (f5.exists()) {
                    String t5 = extract(HWPReader.fromFile(f5.getPath()));
                    long[] hangulMiss = new long[1];
                    double c = coverage(t, t5, hangulMiss);
                    check(c >= HWP3_THRESHOLD, "hwp3/" + trim(base),
                            String.format("cov=%.2f%% 한글누락=%d 문단=%d", c, hangulMiss[0], h.getParagraphCount()));
                } else {
                    check(t.length() > 0, "hwp3/" + trim(base), "chars=" + t.length());
                }
            } catch (Throwable ex) {
                check(false, "hwp3/" + trim(base), "EX:" + ex);
            }
        }
    }

    /** 5. 통합 진입점 라우팅. */
    private static void testUnifiedRouting() throws Exception {
        System.out.println("\n========== 5. 통합 진입점 라우팅 (HWPLibReader.fromBytes) ==========");
        File[] hwp5 = list("hwp5");
        if (hwp5.length > 0) {
            try {
                HWPFile h = HWPLibReader.fromBytes(readAll(hwp5[0]));
                check(h != null, "route hwp5/" + trim(hwp5[0].getName()), "HWPFile 반환");
            } catch (Throwable ex) {
                check(false, "route hwp5", "EX:" + ex);
            }
        }
        File[] hwpml = list("hwpml");
        if (hwpml.length > 0) {
            try {
                HWPFile h = HWPLibReader.fromBytes(readAll(hwpml[0]));
                check(h != null, "route hwpml/" + trim(hwpml[0].getName()), "HWPFile 반환");
            } catch (Throwable ex) {
                check(false, "route hwpml", "EX:" + ex);
            }
        }
    }

    // ---- 헬퍼 ----

    private static String extract(HWPFile h) throws Exception {
        return TextExtractor.extract(h, TextExtractMethod.InsertControlTextBetweenParagraphText);
    }

    /** 공백/제어문자/객체대체문자를 제외한 문자 멀티셋 기준 커버리지(%)와 본문 한글 누락 수. */
    private static double coverage(String got, String ref, long[] hangulMissOut) {
        Map<Integer, Integer> h5 = hist(ref);
        Map<Integer, Integer> h3 = hist(got);
        long total = 0, covered = 0, hangulMiss = 0;
        for (Map.Entry<Integer, Integer> e : h5.entrySet()) {
            int need = e.getValue();
            int have = h3.getOrDefault(e.getKey(), 0);
            total += need;
            covered += Math.min(need, have);
            int cp = e.getKey();
            if (have < need && cp >= 0xAC00 && cp <= 0xD7A3) {
                hangulMiss += need - have;
            }
        }
        if (hangulMissOut != null) {
            hangulMissOut[0] = hangulMiss;
        }
        return total == 0 ? 100.0 : 100.0 * covered / total;
    }

    private static Map<Integer, Integer> hist(String s) {
        Map<Integer, Integer> m = new HashMap<Integer, Integer>();
        s.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t' || cp == ' ' || cp == '￼' || cp == 0xA0) {
                return;
            }
            m.merge(cp, 1, Integer::sum);
        });
        return m;
    }

    private static File[] list(String dir) {
        File[] fs = new File(CORPUS + dir).listFiles(f -> !f.isDirectory());
        if (fs == null) {
            return new File[0];
        }
        Arrays.sort(fs);
        return fs;
    }

    private static void check(boolean ok, String label, String detail) {
        System.out.printf("  [%s] %-44s %s%n", ok ? "PASS" : "FAIL", label, detail);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
    }

    private static byte[] readAll(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream is = new FileInputStream(f)) {
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        }
        return bos.toByteArray();
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(0, i);
    }

    private static String trim(String s) {
        return s.length() > 28 ? s.substring(0, 28) + "…" : s;
    }
}
