package Depolita.domain.app.port;

import java.nio.file.Path;

public interface ImageBuilder {
    String build(Path contextDir, String tag);
    void remove(String tag);
}