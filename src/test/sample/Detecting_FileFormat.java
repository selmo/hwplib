package sample;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.FileFormat;
import kr.dogfoot.hwplib.reader.HWPLibReader;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 파일 형식 자동 판별 및 통합 리더 사용 예제.
 *
 * <p>한글 5.0 / 한글 3.x / HWPML을 자동으로 판별하여 공통 객체 모델({@link HWPFile})로
 * 읽는다. (한글 3.x · HWPML 본문 파싱은 이후 단계에서 구현 예정 — P0 골격)</p>
 */
public class Detecting_FileFormat {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: Detecting_FileFormat <file>");
            return;
        }

        byte[] data = readAll(new FileInputStream(args[0]));

        FileFormat format = HWPLibReader.detectFormat(data);
        System.out.println("Detected format: " + format);

        try {
            HWPFile hwpFile = HWPLibReader.fromBytes(data);
            System.out.println("Read OK. sections="
                    + hwpFile.getBodyText().getSectionList().size());
        } catch (UnsupportedOperationException e) {
            System.out.println("Not yet implemented: " + e.getMessage());
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } finally {
            is.close();
        }
    }
}
