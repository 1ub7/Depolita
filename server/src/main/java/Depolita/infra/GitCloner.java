package Depolita.infra;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GitCloner {
    public Path clone(String repoUrl) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("depolita-");
            try (Git ignored = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(dir.toFile())
                    .setDepth(1)
                    .call()) {
                return dir;
            }
        } catch (Exception e) {
            cleanup(dir);
            throw new CustomException(ErrorCode.GIT_CLONE_FAILED, e);
        }
    }

    public void cleanup(Path dir) {
        if (dir == null) return;
        try {
            FileUtils.delete(dir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        } catch (IOException ignored) {}
    }
}
