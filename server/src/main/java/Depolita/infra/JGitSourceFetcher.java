package Depolita.infra;

import Depolita.domain.app.port.FetchedSource;
import Depolita.domain.app.port.SourceFetcher;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JGitSourceFetcher implements SourceFetcher {
    @Override
    public FetchedSource fetch(String repoUrl) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("depolita-");
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(dir.toFile())
                    .setDepth(1)
                    .call()) {
                ObjectId head = git.getRepository().resolve(Constants.HEAD);
                return new FetchedSource(dir, head == null ? null : head.name());
            }
        } catch (Exception e) {
            cleanup(dir);
            throw new CustomException(ErrorCode.GIT_CLONE_FAILED, e);
        }
    }

    @Override
    public void cleanup(Path dir) {
        if (dir == null) return;
        try {
            FileUtils.delete(dir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
        } catch (IOException ignored) {}
    }
}
