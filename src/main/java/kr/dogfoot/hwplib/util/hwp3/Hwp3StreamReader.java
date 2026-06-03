package kr.dogfoot.hwplib.util.hwp3;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/**
 * 한글 3.x 바이너리 데이터를 읽기 위한 리틀 엔디언 스트림 리더.
 *
 * <p>규격: 「한글 문서 파일 형식 3.0 / HWPML」 revision 1.2, Part I §2(자료형).
 * 모든 다중 바이트 값은 리틀 엔디언으로 저장된다.</p>
 */
public class Hwp3StreamReader {
    private final byte[] data;
    private int position;

    public Hwp3StreamReader(byte[] data) {
        this(data, 0);
    }

    public Hwp3StreamReader(byte[] data, int position) {
        this.data = data;
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int remaining() {
        return data.length - position;
    }

    public boolean isEndOfStream() {
        return position >= data.length;
    }

    /**
     * 부호 없는 1바이트(byte)를 읽는다.
     */
    public int readUInt8() {
        return data[position++] & 0xFF;
    }

    /**
     * 부호 있는 1바이트(sbyte)를 읽는다.
     */
    public int readInt8() {
        return data[position++];
    }

    /**
     * 부호 없는 2바이트(word)를 읽는다.
     */
    public int readUInt16() {
        int v = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
        position += 2;
        return v;
    }

    /**
     * 부호 있는 2바이트(sword)를 읽는다.
     */
    public short readInt16() {
        return (short) readUInt16();
    }

    /**
     * 부호 없는 4바이트(dword)를 읽는다.
     */
    public long readUInt32() {
        long v = (data[position] & 0xFFL)
                | ((data[position + 1] & 0xFFL) << 8)
                | ((data[position + 2] & 0xFFL) << 16)
                | ((data[position + 3] & 0xFFL) << 24);
        position += 4;
        return v;
    }

    public byte[] readBytes(int n) {
        byte[] r = new byte[n];
        System.arraycopy(data, position, r, 0, n);
        position += n;
        return r;
    }

    public void skip(int n) {
        position += n;
    }

    /**
     * raw DEFLATE(zlib/gzip 헤더 없는 압축)로 압축된 데이터를 해제한다.
     * 한글 3.x의 압축 영역은 이 방식으로 저장된다.
     *
     * @param input  전체 바이트
     * @param offset 압축 영역 시작 오프셋
     * @return 해제된 바이트
     */
    public static byte[] inflateRaw(byte[] input, int offset) throws Exception {
        Inflater inflater = new Inflater(true);
        inflater.setInput(input, offset, input.length - offset);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, (input.length - offset) * 4));
        byte[] buffer = new byte[65536];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                bos.write(buffer, 0, n);
            }
        } finally {
            inflater.end();
        }
        return bos.toByteArray();
    }
}
