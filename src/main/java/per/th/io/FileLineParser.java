package per.th.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.NoSuchElementException;

/**
 * @author th
 * @date 2023/9/14
 * @see
 * @since
 */
public class FileLineParser implements Closeable {

    private final LineNumberReader reader;
    private final String separator;

    private String line;
    private String[] fields;

    public FileLineParser(URL url, String separator) throws IOException {
        this.reader = new LineNumberReader(new InputStreamReader(url.openStream()));
        this.separator = separator;
    }

    public boolean next() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            this.fields = line.split(separator);
            this.line = line;
            return true;
        } else {
            this.fields = null;
            this.line = null;
            return false;
        }
    }

    public String getString(int index) {
        if (fields == null) {
            throw new NoSuchElementException();
        } else if (fields.length <= index) {
            return null;
        } else {
            return fields[index];
        }
    }

    public int getInt(int index) {
        return getInt(index, 10);
    }

    public int getInt(int index, int radio) {
        return 0;
    }

    public int getUnsignedInt(int index) {
        return getUnsignedInt(index, 16);
    }

    public int getUnsignedInt(int index, int radio) {
        return Integer.parseUnsignedInt(fields[index].replaceAll("\\s*", ""), radio);
    }

    public int getLineNumber() {
        return reader.getLineNumber();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
